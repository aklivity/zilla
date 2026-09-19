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

// Test fixture: detects unambiguously via a fixed content-type, contributes a permissive request schema
// (accepts any JSON object) so k3po ITs can exercise the happy path end-to-end, including LlmBeginEx.model
// extraction via a decode-time field observer (mirrors LlmTestConditionalDialect / KafkaExtractTransform).
public final class LlmTestPermissiveDialect implements LlmDialect
{
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String CONTENT_TYPE = "application/vnd.zilla.test-permissive+json";

    private static final String MODEL_NAME = "model";

    private final JsonTransform requestSchemaValidator;

    public LlmTestPermissiveDialect()
    {
        this.requestSchemaValidator =
            JsonSchema.of(LlmTestClientSseDialect.readResource(schemaResource())).validator();
    }

    @Override
    public String name()
    {
        return "test-permissive";
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return CONTENT_TYPE.equals(header(headers, HEADER_CONTENT_TYPE));
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new LlmModelExtractTransform(envelope) : LlmDialectTransforms.identity();
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
        return kind == Kind.REQUEST ? requestSchemaValidator : LlmDialectTransforms.identity();
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
        return LlmTestPermissiveDialect.class.getResource("test.permissive.request.schema.json");
    }

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }
}
