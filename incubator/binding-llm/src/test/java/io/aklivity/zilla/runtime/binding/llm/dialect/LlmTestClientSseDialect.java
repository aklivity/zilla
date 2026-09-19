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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * A test-only dialect for the {@code llm client} k3po scenarios, fixed via {@code options.dialect} rather
 * than {@link #detect(JsonEnvelope)} (never called for this dialect, so it unconditionally declines), so
 * the client's same-dialect passthrough path can be exercised without depending on a real dialect
 * implementation (e.g. openai, anthropic).
 */
public final class LlmTestClientSseDialect implements LlmDialect
{
    private final JsonTransform schemaValidator;

    public LlmTestClientSseDialect()
    {
        this.schemaValidator = JsonSchema.of(readResource(schemaResource())).validator();
    }

    @Override
    public String name()
    {
        return "test-client-sse";
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return false;
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplyValidator(
        Kind kind,
        JsonEnvelope envelope)
    {
        return supplyDecoder(kind, envelope);
    }

    @Override
    public JsonTransform supplyEncoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return LlmDialectTransforms.identity();
    }

    @Override
    public JsonTransform supplySchemaValidator(
        Kind kind)
    {
        return schemaValidator;
    }

    @Override
    public DirectBufferEx terminator(
        Kind kind)
    {
        return null;
    }

    @Override
    public JsonObject decodeMessage(
        String data)
    {
        return Json.createObjectBuilder().build();
    }

    @Override
    public String encodeMessage(
        JsonObject message)
    {
        return "{}";
    }

    static URL schemaResource()
    {
        return LlmTestClientSseDialect.class.getResource("test.client.sse.schema.json");
    }

    static String readResource(
        URL resource)
    {
        String text;
        try (InputStream input = resource.openStream())
        {
            text = new String(input.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return text;
    }
}
