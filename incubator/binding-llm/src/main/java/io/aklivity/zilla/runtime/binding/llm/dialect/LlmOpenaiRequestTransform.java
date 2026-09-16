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

import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

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
 * canonical synonym yet, so it -- and every nested value, at any depth -- is forwarded unchanged. Matching a
 * field's full path against this table's top-level-only entries (e.g. {@code $.max_tokens}) naturally scopes
 * the rename to a direct member of the request object; a same-named field nested inside {@code messages} or
 * {@code tools} has a different, non-matching path and is never touched.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each
 * {@link LlmDialect#supplyDecoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}
 * call.
 * </p>
 */
final class LlmOpenaiRequestTransform implements ModelTransform
{
    private static final String[][] RENAMES =
    {
        { "$.max_tokens", "$.maxOutputTokens" },
        { "$.top_p", "$.topP" },
        { "$.n", "$.choiceCount" },
        { "$.presence_penalty", "$.presencePenalty" },
        { "$.frequency_penalty", "$.frequencyPenalty" },
        { "$.top_logprobs", "$.topLogprobs" },
        { "$.tool_choice", "$.toolChoice" },
        { "$.response_format", "$.responseFormat" },
    };

    private final boolean toCanonical;
    private final LlmOpenaiSubstitutedSource renamed;

    LlmOpenaiRequestTransform(
        boolean toCanonical)
    {
        this.toCanonical = toCanonical;
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
        if (event == ModelEvent.FIELD)
        {
            final String toPath = rename(source.getPath());
            status = toPath != null
                ? sink.transform(control, renamed.wrap(toPath, source.getValue()), ModelEvent.REPLACED)
                : sink.transform(control, source, event);
        }
        else
        {
            status = sink.transform(control, source, event);
        }
        return status;
    }

    private String rename(
        String path)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : RENAMES)
        {
            if (pair[from].equals(path))
            {
                match = pair[to];
                break;
            }
        }
        return match;
    }
}
