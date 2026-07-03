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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;

public final class AsyncapiDeserializers
{
    private AsyncapiDeserializers()
    {
    }

    public static List<JsonbDeserializer<?>> all(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes)
    {
        return List.of(
            new AsyncapiDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiServerDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiServerVariableDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiChannelDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiOperationDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiMessageDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiTraitDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiParameterDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiComponentsDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiSchemaDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiMultiFormatSchemaDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiSchemaItemDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiSecuritySchemeDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiCorrelationIdDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes),
            new AsyncapiReplyDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes));
    }

    static Supplier<Jsonb> plain(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes,
        Class<?>... excludes)
    {
        Jsonb[] cache = new Jsonb[1];
        Set<Class<?>> excluded = Set.of(excludes);

        return () ->
        {
            if (cache[0] == null)
            {
                JsonbDeserializer<?>[] others = all(
                    operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes)
                        .stream()
                        .filter(deserializer -> !excluded.contains(deserializer.getClass()))
                        .toArray(JsonbDeserializer[]::new);
                cache[0] = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withDeserializers(others))
                    .build();
            }
            return cache[0];
        };
    }

    static Map<String, Object> bindings(
        JsonObject object,
        Map<String, Class<?>> bindingTypes,
        Jsonb plain)
    {
        Map<String, Object> bindings = null;

        JsonValue bindingsValue = object.get("bindings");
        if (bindingsValue != null && bindingsValue.getValueType() == JsonValue.ValueType.OBJECT)
        {
            for (Map.Entry<String, JsonValue> entry : bindingsValue.asJsonObject().entrySet())
            {
                Class<?> bindingType = bindingTypes.get(entry.getKey());
                if (bindingType != null)
                {
                    if (bindings == null)
                    {
                        bindings = new LinkedHashMap<>();
                    }
                    bindings.put(entry.getKey(), plain.fromJson(entry.getValue().toString(), bindingType));
                }
            }
        }

        return bindings;
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
}
