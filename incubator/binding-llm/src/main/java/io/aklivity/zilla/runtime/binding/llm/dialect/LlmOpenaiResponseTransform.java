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
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Renames a handful of OpenAI Chat Completions streaming-chunk members between OpenAI's native field names
 * and the canonical vocabulary this dialect defines a synonym for, matched by each field's own full path:
 * <ul>
 * <li>A direct member of one {@code choices[]} element -- e.g. {@code $.choices[0].index} -- renames
 * {@code index}/{@code choiceIndex} (the design's canonical vocabulary names this {@code choiceIndex}
 * precisely so multiple choices, {@code n > 1}, carry an unambiguous index rather than relying on array
 * position) and {@code finish_reason}/{@code finishReason} -- the latter also remapping its
 * {@code "tool_calls"}/{@code "tool_call"} value (every other value, {@code stop}/{@code length}/
 * {@code content_filter}, is already identical in both vocabularies).</li>
 * <li>A direct member of the {@code usage} object -- e.g. {@code $.usage.prompt_tokens} -- renames
 * {@code prompt_tokens}/{@code inputTokens}, {@code completion_tokens}/{@code outputTokens},
 * {@code total_tokens}/{@code totalTokens}.</li>
 * </ul>
 * Matching the field's full path, rather than tracking nesting depth, disambiguates a same-named field
 * nested deeper -- e.g. a streamed {@code delta.tool_calls[].index} entry has path
 * {@code $.choices[0].delta.tool_calls[0].index}, which is not a direct {@code choices[]} member and is
 * left untouched.
 * <p>
 * Everything else -- {@code id}, {@code object}, {@code created}, {@code model} at the root; {@code role},
 * {@code content} and the whole {@code tool_calls[]} structure (including each entry's streamed
 * {@code function.arguments} fragment) inside {@code delta} -- has no established canonical synonym yet and
 * is forwarded unchanged, at any depth. The {@code logprobs} member of a choice is likewise left unrenamed:
 * its value is an object, and renaming its key without touching its contents would require redirecting a
 * container-valued field, which this dialect's field-by-field, scalar-value-oriented transform contract does
 * not support.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each
 * {@link LlmDialect#supplyDecoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}
 * call.
 * </p>
 * <p>
 * The literal {@code [DONE]} sentinel that terminates an OpenAI stream is not JSON and never reaches this
 * transform -- it fails to parse as JSON at all, so the caller recognizes and forwards it verbatim before
 * ever invoking a model pipeline. Every other response chunk, same-dialect or cross-dialect, is a genuine
 * JSON document and is schema-validated through a model pipeline; only {@code [DONE]} is exempt, and only
 * because it is not JSON to begin with, not because same-dialect traffic skips validation.
 * </p>
 */
final class LlmOpenaiResponseTransform implements ModelTransform
{
    private static final String CHOICES_PREFIX = "$.choices[";
    private static final String USAGE_PREFIX = "$.usage.";

    private static final String[][] CHOICE_RENAMES =
    {
        { "index", "choiceIndex" },
        { "finish_reason", "finishReason" },
    };

    private static final String[][] USAGE_RENAMES =
    {
        { "prompt_tokens", "inputTokens" },
        { "completion_tokens", "outputTokens" },
        { "total_tokens", "totalTokens" },
    };

    private static final String FINISH_REASON_NATIVE = "finish_reason";
    private static final String FINISH_REASON_CANONICAL = "finishReason";
    private static final String FINISH_REASON_VALUE_NATIVE = "tool_calls";
    private static final String FINISH_REASON_VALUE_CANONICAL = "tool_call";

    private final boolean toCanonical;
    private final LlmOpenaiSubstitutedSource renamed;
    private final UnsafeBufferEx nativeFinishReasonValue;
    private final UnsafeBufferEx canonicalFinishReasonValue;

    LlmOpenaiResponseTransform(
        boolean toCanonical)
    {
        this.toCanonical = toCanonical;
        this.renamed = new LlmOpenaiSubstitutedSource();
        this.nativeFinishReasonValue = new UnsafeBufferEx(FINISH_REASON_VALUE_NATIVE.getBytes(UTF_8));
        this.canonicalFinishReasonValue = new UnsafeBufferEx(FINISH_REASON_VALUE_CANONICAL.getBytes(UTF_8));
    }

    @Override
    public ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        final ModelStatus status;
        if (event == ModelEvent.FIELD)
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
        final String choiceMember = choiceMember(path);
        final String usageMember = choiceMember == null ? usageMember(path) : null;

        final ModelStatus status;
        if (choiceMember != null)
        {
            status = onChoiceMember(control, source, choiceMember, sink);
        }
        else if (usageMember != null)
        {
            final String toName = rename(USAGE_RENAMES, usageMember);
            status = toName != null
                ? sink.transform(control, renamed.wrap(USAGE_PREFIX + toName, source.getValue()), ModelEvent.REPLACED)
                : sink.transform(control, source, ModelEvent.FIELD);
        }
        else
        {
            status = sink.transform(control, source, ModelEvent.FIELD);
        }
        return status;
    }

    private ModelStatus onChoiceMember(
        ModelController control,
        ModelSource source,
        String member,
        ModelSink sink)
    {
        final String path = source.getPath();
        final String basePath = path.substring(0, path.length() - member.length());
        final String toName = rename(CHOICE_RENAMES, member);
        final boolean finishReason = (toCanonical ? FINISH_REASON_NATIVE : FINISH_REASON_CANONICAL).equals(member);

        final ModelStatus status;
        if (finishReason)
        {
            status = onFinishReason(control, source, basePath, toName, sink);
        }
        else if (toName != null)
        {
            status = sink.transform(control, renamed.wrap(basePath + toName, source.getValue()), ModelEvent.REPLACED);
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
        String basePath,
        String toName,
        ModelSink sink)
    {
        final DirectBufferEx value = source.getValue();
        final String text = value.getStringWithoutLengthUtf8(0, value.capacity());
        final boolean remapValue = (toCanonical ? FINISH_REASON_VALUE_NATIVE : FINISH_REASON_VALUE_CANONICAL).equals(text);

        final ModelStatus status;
        if (toName != null || remapValue)
        {
            final String toPath = toName != null ? basePath + toName : source.getPath();
            final DirectBufferEx toValue = remapValue
                ? toCanonical ? canonicalFinishReasonValue : nativeFinishReasonValue
                : value;
            status = sink.transform(control, renamed.wrap(toPath, toValue), ModelEvent.REPLACED);
        }
        else
        {
            status = sink.transform(control, source, ModelEvent.FIELD);
        }
        return status;
    }

    // checks the direct-member shape (no further '.'/'[' past the candidate start) using indexOf(int,
    // int) against the original path before ever substring()-ing it, so the many deeply-nested fields
    // that share this prefix but aren't direct members (delta.tool_calls[].index and the like) cost no
    // allocation -- only a genuine direct member (a handful of names per element) pays for its own String
    private static String choiceMember(
        String path)
    {
        String member = null;
        if (path.startsWith(CHOICES_PREFIX))
        {
            final int bracket = path.indexOf(']', CHOICES_PREFIX.length());
            if (bracket != -1 && path.length() > bracket + 2 && path.charAt(bracket + 1) == '.')
            {
                final int from = bracket + 2;
                if (path.indexOf('.', from) == -1 && path.indexOf('[', from) == -1)
                {
                    member = path.substring(from);
                }
            }
        }
        return member;
    }

    private static String usageMember(
        String path)
    {
        String member = null;
        if (path.startsWith(USAGE_PREFIX))
        {
            final int from = USAGE_PREFIX.length();
            if (path.indexOf('.', from) == -1 && path.indexOf('[', from) == -1)
            {
                member = path.substring(from);
            }
        }
        return member;
    }

    private String rename(
        String[][] table,
        String key)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : table)
        {
            if (pair[from].equals(key))
            {
                match = pair[to];
                break;
            }
        }
        return match;
    }
}
