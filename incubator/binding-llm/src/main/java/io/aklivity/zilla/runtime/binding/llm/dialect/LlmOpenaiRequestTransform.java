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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonSource;

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
 * canonical synonym yet, so it -- and every nested value, at any depth -- is forwarded unchanged. Only a
 * top-level (depth-1) SCALAR member is ever offered to the rename table -- a same-named field nested inside
 * {@code messages} or {@code tools}, or a top-level member whose own value is an object/array, is never
 * touched; see {@link LlmRequestFieldTransform} for the depth-1-scalar-only interception mechanism.
 * </p>
 * <p>
 * Alongside the rename/forward decision, {@code model} is observed and copied into the supplied
 * {@link JsonEnvelope} under the same name -- mirroring how a Kafka cache model's {@code extractKey}/
 * {@code extractHeaders} transform observes a field and copies its value into an envelope while it flows
 * through unchanged -- so a caller (e.g. stamping {@code LlmBeginEx.model}) reads it back off the envelope
 * without buffering the whole request first just to peek at one field.
 * </p>
 * <p>
 * One instance decodes (native to canonical) or encodes (canonical to native) depending on the direction
 * supplied at construction; a fresh instance backs each
 * {@link LlmDialect#supplyDecoder(LlmDialect.Kind, JsonEnvelope)}/
 * {@link LlmDialect#supplyEncoder(LlmDialect.Kind, JsonEnvelope)} call.
 * </p>
 */
final class LlmOpenaiRequestTransform extends LlmRequestFieldTransform
{
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

    private static final String MODEL_NAME = "model";

    private final boolean toCanonical;
    private final JsonEnvelope envelope;

    LlmOpenaiRequestTransform(
        boolean toCanonical,
        JsonEnvelope envelope)
    {
        this.toCanonical = toCanonical;
        this.envelope = envelope;
    }

    @Override
    protected String rename(
        CharSequence key)
    {
        final int from = toCanonical ? 0 : 1;
        final int to = toCanonical ? 1 : 0;

        String match = null;
        for (String[] pair : RENAMES)
        {
            if (contentEquals(key, pair[from]))
            {
                match = pair[to];
                break;
            }
        }
        return match;
    }

    @Override
    protected void onValue(
        CharSequence key,
        JsonSource source,
        JsonEvent event)
    {
        if (contentEquals(key, MODEL_NAME) && event == JsonEvent.VALUE_STRING)
        {
            envelope.set(MODEL_NAME, new UnsafeBufferEx(source.getString().getBytes(UTF_8)));
        }
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    private static boolean contentEquals(
        CharSequence key,
        String name)
    {
        boolean matches = key.length() == name.length();
        for (int i = 0; matches && i < name.length(); i++)
        {
            matches = key.charAt(i) == name.charAt(i);
        }
        return matches;
    }
}
