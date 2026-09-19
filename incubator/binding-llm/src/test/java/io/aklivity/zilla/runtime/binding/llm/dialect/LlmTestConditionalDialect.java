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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

public final class LlmTestConditionalDialect implements LlmDialect
{
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_PATH = ":path";
    private static final String METHOD_POST = "POST";
    private static final String PATH_TEST = "/v1/test";

    private static final String MODEL_NAME = "model";

    private static final JsonTransform PERMISSIVE_SCHEMA = JsonSchema.of("{}").validator();

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public boolean detect(
        JsonEnvelope headers)
    {
        return METHOD_POST.equals(header(headers, HEADER_METHOD)) && PATH_TEST.equals(header(headers, HEADER_PATH));
    }

    @Override
    public JsonTransform supplyDecoder(
        Kind kind,
        JsonEnvelope envelope)
    {
        return kind == Kind.REQUEST ? new ModelExtractTransform(MODEL_NAME, envelope) : LlmDialectTransforms.identity();
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

    private static String header(
        JsonEnvelope headers,
        String name)
    {
        DirectBufferEx value = headers.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    // mirrors KafkaExtractTransform (runtime/binding-kafka/.../cache/KafkaExtractTransform.java): observes
    // a top-level scalar member named name, copies its value into envelope, forwards the field unchanged
    private static final class ModelExtractTransform extends LlmRequestFieldTransform
    {
        private final String name;
        private final JsonEnvelope envelope;

        private ModelExtractTransform(
            String name,
            JsonEnvelope envelope)
        {
            this.name = name;
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
            if (contentEquals(key, name) && event == JsonEvent.VALUE_STRING)
            {
                envelope.set(name, new UnsafeBufferEx(source.getString().getBytes(UTF_8)));
            }
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        private static boolean contentEquals(
            CharSequence key,
            String value)
        {
            boolean matches = key.length() == value.length();
            for (int i = 0; matches && i < value.length(); i++)
            {
                matches = key.charAt(i) == value.charAt(i);
            }
            return matches;
        }
    }
}
