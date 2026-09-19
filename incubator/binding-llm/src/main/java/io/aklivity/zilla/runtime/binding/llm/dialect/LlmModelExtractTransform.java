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
 * Observes the top-level {@code model} field of a request -- a direct child of the root object in both the
 * OpenAI Chat Completions and Anthropic Messages request shapes -- copying its value into the supplied
 * {@link JsonEnvelope} while forwarding every field unchanged, at any depth: no canonical renaming. Mirrors
 * how a Kafka cache model's {@code extractKey}/{@code extractHeaders} transform observes a field and copies
 * its value into an envelope while it flows through unchanged.
 * <p>
 * {@code model} sits at the identical top-level key in every dialect this binding supports so far, so one
 * dialect-neutral instance backs every {@link LlmDialect#supplyValidator(LlmDialect.Kind, JsonEnvelope)}
 * implementation rather than duplicating identical extraction logic per dialect.
 * </p>
 */
final class LlmModelExtractTransform extends LlmRequestFieldTransform
{
    private static final String MODEL_NAME = "model";

    private final JsonEnvelope envelope;

    LlmModelExtractTransform(
        JsonEnvelope envelope)
    {
        this.envelope = envelope;
    }

    @Override
    protected String rename(
        CharSequence key)
    {
        return null;
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
        return true;
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
