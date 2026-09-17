/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Renames a handful of Anthropic Messages API streaming-event members between Anthropic's native field names
 * and the canonical vocabulary this dialect defines a synonym for. The framing layer ({@code
 * LlmSseContentDecoder}/{@code LlmSseContentEncoder}) delivers one SSE event's {@code data} payload to this
 * transform as a single JSON document per call, so renaming acts purely on the fields of whichever event's
 * JSON document currently reaches it -- the SSE {@code event:} name itself ({@code message_start}/
 * {@code content_block_start}/{@code content_block_delta}/{@code content_block_stop}/{@code message_delta}/
 * {@code message_stop}) is a framing-level concern with no bearing on any rename here:
 * <ul>
 * <li>The top-level content-block position -- {@code $.index} on a {@code content_block_start},
 * {@code content_block_delta} or {@code content_block_stop} event -- renames {@code index}/{@code blockId},
 * matching the block-lifecycle vocabulary this binding's canonical representation is itself modeled on.</li>
 * <li>A {@code message_delta} event's {@code $.delta.stop_reason} renames to {@code $.delta.finishReason},
 * remapping its value: {@code max_tokens}/{@code length} and {@code tool_use}/{@code tool_call} round-trip
 * losslessly, as does {@code end_turn}/{@code stop}. {@code stop_sequence} also maps to the canonical
 * {@code stop} on decode, but since two native values collapse onto that one canonical value, encoding
 * {@code stop} back to native always yields {@code end_turn} -- the same choice already made by the
 * {@code stop_reason} handling this dialect's canonical mapping agrees with.</li>
 * <li>{@code input_tokens}/{@code inputTokens} and {@code output_tokens}/{@code outputTokens} rename wherever
 * they appear as a direct member of a {@code usage} object, regardless of nesting depth -- {@code
 * message_start} nests its usage under {@code $.message.usage}, while {@code message_delta} carries its own
 * at {@code $.usage}, so the match is by path suffix rather than a fixed depth.</li>
 * </ul>
 * Everything else -- {@code id}, {@code model}, {@code role} on {@code message_start};
 * {@code content_block.type}/{@code id}/{@code name} on {@code content_block_start}; {@code delta.type},
 * {@code delta.text} and {@code delta.partial_json} on {@code content_block_delta} -- has no established
 * canonical synonym yet and is forwarded unchanged, at any depth. This is far less renaming than
 * {@link LlmOpenaiResponseTransform} performs, since Anthropic's own block lifecycle is already this
 * canonical representation's skeleton.
 * <p>
 * On decode only, the one exception to "the SSE {@code event:} name has no bearing here" above: each
 * event's own {@code $.type} field (e.g. {@code "content_block_delta"}) is checked against the SSE
 * {@code event:} name the framing layer captured for it, stashed under {@code event} in the supplied
 * {@link ModelEnvelope} by whichever caller drives the content decoder (mirroring how {@code model} is
 * captured from the request in {@link LlmAnthropicRequestTransform}). A mismatch rejects the value -- a
 * well-behaved backend never sends one, so this only ever fires against a malformed or malicious upstream,
 * which is exactly the boundary this decode direction sits on. {@code type} itself is still forwarded
 * unchanged; nothing renames it. Encoding is unaffected: this dialect authors both the outgoing
 * {@code event:} line and its {@code type} field from the same {@code LlmFlushExFW} kind, so they cannot
 * disagree the way untrusted inbound bytes can.
 * </p>
 * <p>
 * {@link LlmOpenaiSubstitutedSource} is reused here despite its name -- it is a generic {@code path}/
 * {@code value} substitution {@link ModelSource} with no OpenAI-specific behavior, so forking a byte-identical
 * copy under this dialect's own name would add duplication with no behavioral difference.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each
 * {@link LlmDialect#supplyDecoder(LlmDialect.Kind, ModelEnvelope)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind, ModelEnvelope)} call.
 * </p>
 */
final class LlmAnthropicResponseTransform implements ModelTransform
{
    private static final String[][] TOP_LEVEL_RENAMES =
    {
        { "$.index", "$.blockId" },
    };

    private static final String USAGE_INPUT_TOKENS_NATIVE_SUFFIX = ".usage.input_tokens";
    private static final String USAGE_INPUT_TOKENS_CANONICAL_SUFFIX = ".usage.inputTokens";
    private static final String USAGE_OUTPUT_TOKENS_NATIVE_SUFFIX = ".usage.output_tokens";
    private static final String USAGE_OUTPUT_TOKENS_CANONICAL_SUFFIX = ".usage.outputTokens";

    private static final String FINISH_REASON_NATIVE_PATH = "$.delta.stop_reason";
    private static final String FINISH_REASON_CANONICAL_PATH = "$.delta.finishReason";

    private static final String TYPE_PATH = "$.type";
    private static final String EVENT_NAME = "event";

    private static final String[][] FINISH_REASON_TO_CANONICAL =
    {
        { "end_turn", "stop" },
        { "max_tokens", "length" },
        { "stop_sequence", "stop" },
        { "tool_use", "tool_call" },
    };

    private static final String[][] FINISH_REASON_TO_NATIVE =
    {
        { "length", "max_tokens" },
        { "tool_call", "tool_use" },
        { "stop", "end_turn" },
    };

    private final boolean toCanonical;
    private final ModelEnvelope envelope;
    private final LlmOpenaiSubstitutedSource renamed;

    LlmAnthropicResponseTransform(
        boolean toCanonical,
        ModelEnvelope envelope)
    {
        this.toCanonical = toCanonical;
        this.envelope = envelope;
        this.renamed = new LlmOpenaiSubstitutedSource();
    }

    @Override
    public ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        final ModelStatus status;
        if (event == ModelEvent.FIELD || event == ModelEvent.REPLACED)
        {
            status = onField(control, source, sink);
        }
        else
        {
            status = sink.transform(control, source, event);
        }
        return status;
    }

    private ModelStatus onField(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        final String path = source.getPath();
        final String topLevelRename = rename(TOP_LEVEL_RENAMES, path);
        final boolean finishReasonPath = (toCanonical ? FINISH_REASON_NATIVE_PATH : FINISH_REASON_CANONICAL_PATH)
            .equals(path);
        final String usageRename = topLevelRename == null && !finishReasonPath ? renameUsageMember(path) : null;
        final boolean typePath = toCanonical && TYPE_PATH.equals(path);

        final ModelStatus status;
        if (topLevelRename != null)
        {
            status = sink.transform(control, renamed.wrap(topLevelRename, source.getValue()), ModelEvent.REPLACED);
        }
        else if (finishReasonPath)
        {
            status = onFinishReason(control, source, sink);
        }
        else if (usageRename != null)
        {
            status = sink.transform(control, renamed.wrap(usageRename, source.getValue()), ModelEvent.REPLACED);
        }
        else if (typePath)
        {
            status = onType(control, source, sink);
        }
        else
        {
            status = sink.transform(control, source, ModelEvent.FIELD);
        }
        return status;
    }

    private ModelStatus onType(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        final DirectBufferEx value = source.getValue();
        final String type = value.getStringWithoutLengthUtf8(0, value.capacity());

        final int eventCount = envelope.count(EVENT_NAME);
        final DirectBufferEx eventValue = eventCount > 0 ? envelope.get(EVENT_NAME, eventCount - 1) : null;
        final String event = eventValue != null ? eventValue.getStringWithoutLengthUtf8(0, eventValue.capacity()) : null;

        final ModelStatus status;
        if (event != null && !event.equals(type))
        {
            control.reject("anthropic response \"type\" (" + type + ") does not match its SSE \"event\" (" + event + ")");
            status = ModelStatus.REJECTED;
        }
        else
        {
            status = sink.transform(control, source, ModelEvent.FIELD);
        }
        return status;
    }

    private ModelStatus onFinishReason(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        final String toPath = toCanonical ? FINISH_REASON_CANONICAL_PATH : FINISH_REASON_NATIVE_PATH;
        final DirectBufferEx value = source.getValue();
        final String text = value.getStringWithoutLengthUtf8(0, value.capacity());
        final String[][] table = toCanonical ? FINISH_REASON_TO_CANONICAL : FINISH_REASON_TO_NATIVE;
        final String remapped = remapValue(table, text);
        final DirectBufferEx toValue = remapped != null ? new UnsafeBufferEx(remapped.getBytes(UTF_8)) : value;
        return sink.transform(control, renamed.wrap(toPath, toValue), ModelEvent.REPLACED);
    }

    private String renameUsageMember(
        String path)
    {
        final String fromInput = toCanonical ? USAGE_INPUT_TOKENS_NATIVE_SUFFIX : USAGE_INPUT_TOKENS_CANONICAL_SUFFIX;
        final String toInput = toCanonical ? USAGE_INPUT_TOKENS_CANONICAL_SUFFIX : USAGE_INPUT_TOKENS_NATIVE_SUFFIX;
        final String fromOutput = toCanonical ? USAGE_OUTPUT_TOKENS_NATIVE_SUFFIX : USAGE_OUTPUT_TOKENS_CANONICAL_SUFFIX;
        final String toOutput = toCanonical ? USAGE_OUTPUT_TOKENS_CANONICAL_SUFFIX : USAGE_OUTPUT_TOKENS_NATIVE_SUFFIX;

        String renamedPath = null;
        if (path.endsWith(fromInput))
        {
            renamedPath = path.substring(0, path.length() - fromInput.length()) + toInput;
        }
        else if (path.endsWith(fromOutput))
        {
            renamedPath = path.substring(0, path.length() - fromOutput.length()) + toOutput;
        }
        return renamedPath;
    }

    private String rename(
        String[][] table,
        String path)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : table)
        {
            if (pair[from].equals(path))
            {
                match = pair[to];
                break;
            }
        }
        return match;
    }

    private static String remapValue(
        String[][] table,
        String value)
    {
        String match = null;
        for (String[] pair : table)
        {
            if (pair[0].equals(value))
            {
                match = pair[1];
                break;
            }
        }
        return match;
    }
}
