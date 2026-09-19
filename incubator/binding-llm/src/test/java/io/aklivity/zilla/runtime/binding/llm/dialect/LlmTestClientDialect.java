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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * A test-only dialect whose content-type has no registered {@code LlmContentCodecSpi}, so the client's
 * opaque-fallback path (no recognized wire framing, forwarded byte-for-byte with no model validation) can
 * be exercised without depending on a real dialect implementation.
 */
public final class LlmTestClientDialect implements LlmDialect
{
    private static final JsonTransform PERMISSIVE_SCHEMA = JsonSchema.of("{}").validator();

    @Override
    public String name()
    {
        return "test-client";
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
        return PERMISSIVE_SCHEMA;
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
}
