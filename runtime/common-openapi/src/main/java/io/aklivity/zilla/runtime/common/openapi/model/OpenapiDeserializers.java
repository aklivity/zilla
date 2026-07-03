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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiExtensionScope;

public final class OpenapiDeserializers
{
    private OpenapiDeserializers()
    {
    }

    static Supplier<Jsonb> plain(
        Map<OpenapiExtensionScope, Map<String, Class<?>>> extensionTypes,
        Map<OpenapiExtensionScope, Map<String, Class<?>>> prefixExtensionTypes,
        Class<?> exclude)
    {
        Jsonb[] cache = new Jsonb[1];

        return () ->
        {
            if (cache[0] == null)
            {
                JsonbDeserializer<?>[] others = all(extensionTypes, prefixExtensionTypes).stream()
                    .filter(deserializer -> deserializer.getClass() != exclude)
                    .toArray(JsonbDeserializer[]::new);
                cache[0] = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withDeserializers(others))
                    .build();
            }
            return cache[0];
        };
    }

    static Map<String, Object> extensions(
        JsonObject object,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes,
        Supplier<Jsonb> plain)
    {
        Map<String, Object> extensions = null;

        for (String name : object.keySet())
        {
            if (name.startsWith("x-"))
            {
                Class<?> extensionType = extensionTypes.get(name);
                if (extensionType != null)
                {
                    if (extensions == null)
                    {
                        extensions = new LinkedHashMap<>();
                    }
                    extensions.put(name, plain.get().fromJson(object.get(name).toString(), extensionType));
                }
            }
        }

        for (Map.Entry<String, Class<?>> prefixExtensionType : prefixExtensionTypes.entrySet())
        {
            String registeredName = prefixExtensionType.getKey();
            String prefix = registeredName.substring(0, registeredName.length() - 1);

            JsonObjectBuilder aggregate = null;
            for (String name : object.keySet())
            {
                if (name.startsWith(prefix))
                {
                    if (aggregate == null)
                    {
                        aggregate = Json.createObjectBuilder();
                    }
                    aggregate.add(name.substring(prefix.length()), object.get(name));
                }
            }

            if (aggregate != null)
            {
                if (extensions == null)
                {
                    extensions = new LinkedHashMap<>();
                }
                extensions.put(registeredName,
                    plain.get().fromJson(aggregate.build().toString(), prefixExtensionType.getValue()));
            }
        }

        return extensions;
    }

    public static List<JsonbDeserializer<?>> all(
        Map<OpenapiExtensionScope, Map<String, Class<?>>> extensionTypes,
        Map<OpenapiExtensionScope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        return List.of(
            new OpenapiDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiServerDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiServerVariableDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiComponentsDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiPathDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiParameterDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiRequestBodyDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiMediaTypeDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiEncodingDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiSchemaDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiResponseDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiHeaderDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiLinkDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiOperationDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiSecuritySchemeDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiOAuthFlowDeserializer(extensionTypes, prefixExtensionTypes),
            new OpenapiOAuthFlowsDeserializer(extensionTypes, prefixExtensionTypes));
    }
}
