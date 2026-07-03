/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.openapi.model;

import java.lang.reflect.Type;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

public final class OpenapiOperationDeserializer implements JsonbDeserializer<OpenapiOperation>
{
    private static final Jsonb PLAIN = JsonbBuilder.create();

    private final Map<String, Class<?>> extensionTypes;

    public OpenapiOperationDeserializer(
        Map<String, Class<?>> extensionTypes)
    {
        this.extensionTypes = extensionTypes;
    }

    @Override
    public OpenapiOperation deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        JsonObject object = parser.getObject();
        OpenapiOperation operation = PLAIN.fromJson(object.toString(), OpenapiOperation.class);
        operation.extensions = OpenapiExtension.capture(object, extensionTypes);
        return operation;
    }
}
