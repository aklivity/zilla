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
 * Renames the top-level members of an OpenAI Chat Completions request between OpenAI's native
 * {@code snake_case} field names and the canonical camelCase vocabulary this dialect defines a synonym for:
 * {@code max_tokens}/{@code maxOutputTokens}, {@code top_p}/{@code topP}, {@code n}/{@code choiceCount},
 * {@code presence_penalty}/{@code presencePenalty}, {@code frequency_penalty}/{@code frequencyPenalty},
 * {@code top_logprobs}/{@code topLogprobs}, {@code tool_choice}/{@code toolChoice},
 * {@code response_format}/{@code responseFormat}.
 * <p>
 * Every other top-level member ({@code model}, {@code messages}, {@code tools}, {@code temperature},
 * {@code stream}, {@code stop}, {@code user}, {@code seed}, {@code logprobs}, ...) has no established
 * canonical synonym yet, so it -- and every nested value, at any depth -- is forwarded unchanged. The
 * transform only ever inspects a member key at depth 1 (a direct child of the request object); nothing
 * inside {@code messages}/{@code tools} is read or altered.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each {@link LlmDialect#supplyDecoder(LlmDialect.Kind)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind)} call and is reused document-to-document via
 * {@link #reset()}.
 * </p>
 */
final class LlmOpenAiRequestTransform implements JsonTransform
{
    private static final int REQUEST_DEPTH = 1;

    private static final String[][] RENAMES =
    {
        { "max_tokens", "maxOutputTokens" },
        { "top_p", "topP" },
        { "n", "choiceCount" },
        { "presence_penalty", "presencePenalty" },
        { "frequency_penalty", "frequencyPenalty" },
        { "top_logprobs", "topLogprobs" },
        { "tool_choice", "toolChoice" },
        { "response_format", "responseFormat" },
    };

    private final boolean toCanonical;
    private final LlmOpenAiStructuredController structured = new LlmOpenAiStructuredController();
    private final LlmOpenAiSubstitutedSource renamed = new LlmOpenAiSubstitutedSource();

    private int depth;

    LlmOpenAiRequestTransform(
        boolean toCanonical)
    {
        this.toCanonical = toCanonical;
    }

    @Override
    public void reset()
    {
        depth = 0;
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
        default:
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
        final Status status;
        if (depth == REQUEST_DEPTH && source.deferredBytes())
        {
            control.consumed(0);
            status = Status.STARVED;
        }
        else if (depth == REQUEST_DEPTH)
        {
            final String rename = rename(source.getStringView());
            status = rename != null
                ? sink.transform(control, renamed.wrap(source, rename), JsonEvent.KEY_NAME)
                : sink.transform(control, source, JsonEvent.KEY_NAME);
        }
        else
        {
            status = sink.transform(control, source, JsonEvent.KEY_NAME);
        }
        return status;
    }

    private String rename(
        CharSequence key)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : RENAMES)
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
