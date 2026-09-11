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

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Renames a handful of OpenAI Chat Completions streaming-chunk members between OpenAI's native field names
 * and the canonical vocabulary this dialect defines a synonym for, at two fixed nesting depths relative to
 * the chunk's root object:
 * <ul>
 * <li>Depth 3 -- a direct member of one {@code choices[]} element -- renames {@code index}/{@code
 * choiceIndex} (the design's canonical vocabulary names this {@code choiceIndex} precisely so multiple
 * choices, {@code n > 1}, carry an unambiguous index rather than relying on array position),
 * {@code logprobs}/{@code logProbability}, and {@code finish_reason}/{@code finishReason} -- the last also
 * remapping its {@code "tool_calls"}/{@code "tool_call"} value (every other value, {@code stop}/{@code
 * length}/{@code content_filter}, is already identical in both vocabularies).</li>
 * <li>Depth 2 -- a direct member of the {@code usage} object -- renames {@code prompt_tokens}/{@code
 * inputTokens}, {@code completion_tokens}/{@code outputTokens}, {@code total_tokens}/{@code totalTokens}.</li>
 * </ul>
 * Both depths are unambiguous without tracking the enclosing container's identity: an array element (a
 * {@code choices[]} entry included) never itself emits a {@link JsonEvent#KEY_NAME}, so any key seen at
 * depth 2 belongs to {@code usage} and any key at depth 3 belongs to a choice.
 * <p>
 * Everything else -- {@code id}, {@code object}, {@code created}, {@code model} at the root; {@code role},
 * {@code content} and the whole {@code tool_calls[]} structure (including each entry's streamed {@code
 * function.arguments} fragment) inside {@code delta}; the {@code logprobs} object's own contents -- has no
 * established canonical synonym yet and is forwarded unchanged, at any depth.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each {@link LlmDialect#supplyDecoder(LlmDialect.Kind)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind)} call and is reused document-to-document via
 * {@link #reset()}.
 * </p>
 * <p>
 * The literal {@code [DONE]} sentinel that terminates an OpenAI stream is not JSON and never reaches this
 * transform -- the same-dialect path this backs forwards the raw SSE payload bytes without ever routing them
 * through a JSON pipeline; only a genuine JSON document reaches {@link #transform}.
 * </p>
 */
final class LlmOpenAiResponseTransform implements JsonTransform
{
    private static final int USAGE_DEPTH = 2;
    private static final int CHOICE_DEPTH = 3;

    private static final String[][] CHOICE_RENAMES =
    {
        { "index", "choiceIndex" },
        { "finish_reason", "finishReason" },
        { "logprobs", "logProbability" },
    };

    private static final String[][] USAGE_RENAMES =
    {
        { "prompt_tokens", "inputTokens" },
        { "completion_tokens", "outputTokens" },
        { "total_tokens", "totalTokens" },
    };

    private static final String[][] FINISH_REASON_VALUES =
    {
        { "tool_calls", "tool_call" },
    };

    private static final String FINISH_REASON_NATIVE = "finish_reason";
    private static final String FINISH_REASON_CANONICAL = "finishReason";

    private final boolean toCanonical;
    private final LlmOpenAiStructuredController structured = new LlmOpenAiStructuredController();
    private final LlmOpenAiSubstitutedSource renamed = new LlmOpenAiSubstitutedSource();

    private int depth;
    private boolean finishReasonValuePending;

    LlmOpenAiResponseTransform(
        boolean toCanonical)
    {
        this.toCanonical = toCanonical;
    }

    @Override
    public void reset()
    {
        depth = 0;
        finishReasonValuePending = false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final JsonController upstream = structured.wrap(control);
        final Status status;
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            status = sink.transform(upstream, source, event);
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            status = sink.transform(upstream, source, event);
            break;
        case KEY_NAME:
            status = onKey(upstream, source, sink);
            break;
        case VALUE_STRING:
            status = onValueString(upstream, source, sink);
            break;
        default:
            finishReasonValuePending = false;
            status = sink.transform(upstream, source, event);
            break;
        }
        return status;
    }

    private Status onKey(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        final String[][] table = renamesAtDepth(depth);
        final Status status;
        if (table != null && source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else if (table != null)
        {
            final CharSequence key = source.getStringView();
            finishReasonValuePending = depth == CHOICE_DEPTH && isFinishReasonKey(key);

            final String rename = rename(table, key);
            status = rename != null
                ? sink.transform(control, renamed.wrap(source, rename), JsonEvent.KEY_NAME)
                : sink.transform(control, source, JsonEvent.KEY_NAME);
        }
        else
        {
            finishReasonValuePending = false;
            status = sink.transform(control, source, JsonEvent.KEY_NAME);
        }
        return status;
    }

    private Status onValueString(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        final Status status;
        if (finishReasonValuePending && source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else if (finishReasonValuePending)
        {
            finishReasonValuePending = false;
            final String rename = rename(FINISH_REASON_VALUES, source.getStringView());
            status = rename != null
                ? sink.transform(control, renamed.wrap(source, rename), JsonEvent.VALUE_STRING)
                : sink.transform(control, source, JsonEvent.VALUE_STRING);
        }
        else
        {
            status = sink.transform(control, source, JsonEvent.VALUE_STRING);
        }
        return status;
    }

    private String[][] renamesAtDepth(
        int depth)
    {
        final String[][] table;
        if (depth == CHOICE_DEPTH)
        {
            table = CHOICE_RENAMES;
        }
        else if (depth == USAGE_DEPTH)
        {
            table = USAGE_RENAMES;
        }
        else
        {
            table = null;
        }
        return table;
    }

    private boolean isFinishReasonKey(
        CharSequence key)
    {
        return (toCanonical ? FINISH_REASON_NATIVE : FINISH_REASON_CANONICAL).contentEquals(key);
    }

    private String rename(
        String[][] table,
        CharSequence key)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : table)
        {
            if (pair[from].contentEquals(key))
            {
                match = pair[to];
                break;
            }
        }
        return match;
    }
}
