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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Renames the top-level members of an Anthropic Messages API request between Anthropic's native
 * {@code snake_case} field names and the canonical camelCase vocabulary this dialect defines a synonym for:
 * {@code max_tokens}/{@code maxOutputTokens}, {@code top_p}/{@code topP}, {@code tool_choice}/
 * {@code toolChoice} -- the very same canonical names {@link LlmOpenaiRequestTransform} renames its own
 * OpenAI-native equivalents to, so a request round-tripped through the canonical form reads identically
 * regardless of which dialect produced it.
 * <p>
 * Every other top-level member ({@code model}, {@code messages}, {@code system}, {@code temperature},
 * {@code top_k}, {@code stop_sequences}, {@code stream}, {@code tools}, {@code user}, ...) has no established
 * canonical synonym yet, so it -- and every nested value, at any depth -- is forwarded unchanged.
 * {@code top_k} has no OpenAI equivalent to align a canonical name with, and {@code stop_sequences} mirrors
 * OpenAI's own {@code stop}, which likewise has no canonical synonym -- both are left under their native
 * member name rather than inventing a canonical one unilaterally. Matching a field's full path against this
 * table's top-level-only entries (e.g. {@code $.max_tokens}) naturally scopes the rename to a direct member
 * of the request object; a same-named field nested inside {@code messages} or {@code tools} has a different,
 * non-matching path and is never touched.
 * </p>
 * <p>
 * Alongside the rename/forward decision, {@code model} is observed and copied into the supplied
 * {@link ModelEnvelope} under the same name -- mirroring how a Kafka cache model's {@code extractKey}/
 * {@code extractHeaders} transform observes a field and copies its value into an envelope while it flows
 * through unchanged -- so a caller (e.g. stamping {@code LlmBeginEx.model}) reads it back off the envelope
 * without buffering the whole request first just to peek at one field.
 * </p>
 * <p>
 * {@link LlmOpenaiSubstitutedSource} is reused here despite its name -- it is a generic {@code path}/
 * {@code value} substitution {@link ModelSource} with no OpenAI-specific behavior, so forking a byte-identical
 * copy under this dialect's own name would add duplication with no behavioral difference.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each
 * {@link LlmDialect#supplyDecoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind, io.aklivity.zilla.runtime.engine.model.ModelEnvelope)}
 * call.
 * </p>
 */
final class LlmAnthropicRequestTransform implements ModelTransform
{
    private static final String[][] RENAMES =
    {
        { "$.max_tokens", "$.maxOutputTokens" },
        { "$.top_p", "$.topP" },
        { "$.tool_choice", "$.toolChoice" },
    };

    private static final String MODEL_PATH = "$.model";
    private static final String MODEL_NAME = "model";

    private final boolean toCanonical;
    private final ModelEnvelope envelope;
    private final LlmOpenaiSubstitutedSource renamed;

    LlmAnthropicRequestTransform(
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
        if (event == ModelEvent.FIELD)
        {
            if (MODEL_PATH.equals(source.getPath()))
            {
                envelope.set(MODEL_NAME, source.getValue());
            }

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
