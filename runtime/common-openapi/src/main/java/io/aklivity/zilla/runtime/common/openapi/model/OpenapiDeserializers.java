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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;

public final class OpenapiDeserializers
{
    private OpenapiDeserializers()
    {
    }

    static Supplier<Jsonb> plain(
        Map<String, Class<?>> extensionTypes,
        Class<?> exclude)
    {
        Jsonb[] cache = new Jsonb[1];

        return () ->
        {
            if (cache[0] == null)
            {
                JsonbDeserializer<?>[] others = all(extensionTypes).stream()
                    .filter(deserializer -> deserializer.getClass() != exclude)
                    .toArray(JsonbDeserializer[]::new);
                cache[0] = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withDeserializers(others))
                    .build();
            }
            return cache[0];
        };
    }

    public static List<JsonbDeserializer<?>> all(
        Map<String, Class<?>> extensionTypes)
    {
        return List.of(
            new OpenapiDeserializer(extensionTypes),
            new OpenapiServerDeserializer(extensionTypes),
            new OpenapiServerVariableDeserializer(extensionTypes),
            new OpenapiComponentsDeserializer(extensionTypes),
            new OpenapiPathDeserializer(extensionTypes),
            new OpenapiParameterDeserializer(extensionTypes),
            new OpenapiRequestBodyDeserializer(extensionTypes),
            new OpenapiMediaTypeDeserializer(extensionTypes),
            new OpenapiEncodingDeserializer(extensionTypes),
            new OpenapiSchemaDeserializer(extensionTypes),
            new OpenapiResponseDeserializer(extensionTypes),
            new OpenapiHeaderDeserializer(extensionTypes),
            new OpenapiLinkDeserializer(extensionTypes),
            new OpenapiOperationDeserializer(extensionTypes),
            new OpenapiSecuritySchemeDeserializer(extensionTypes));
    }
}
