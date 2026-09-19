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

import java.net.URL;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * A second test-only dialect, distinct from {@link LlmTestClientSseDialect} only by {@link #name()}, so an
 * {@code llm client}'s cross-dialect branch (inbound dialect differs from the configured dialect) can be
 * exercised without depending on a real second dialect implementation (e.g. anthropic). Both directions'
 * transforms are still identity, so this proves the dialect-comparison branch and the chained pipeline
 * plumbing without claiming semantic translation.
 */
public final class LlmTestClientSseAltDialect implements LlmDialect
{
    private final JsonTransform schemaValidator;

    public LlmTestClientSseAltDialect()
    {
        this.schemaValidator = JsonSchema.of(LlmTestClientSseDialect.readResource(schemaResource())).validator();
    }

    @Override
    public String name()
    {
        return "test-client-sse-alt";
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
        return LlmTestClientSseAltDialect.class.getResource("test.client.sse.schema.json");
    }
}
