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
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;

public final class AsyncapiBindingDeserializers
{
    private AsyncapiBindingDeserializers()
    {
    }

    public static List<JsonbDeserializer<?>> all(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes)
    {
        return List.of(
            new AsyncapiOperationDeserializer(operationBindingTypes, messageBindingTypes, serverBindingTypes),
            new AsyncapiMessageDeserializer(operationBindingTypes, messageBindingTypes, serverBindingTypes),
            new AsyncapiServerDeserializer(operationBindingTypes, messageBindingTypes, serverBindingTypes));
    }

    static Supplier<Jsonb> plain(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Class<?> exclude)
    {
        Jsonb[] cache = new Jsonb[1];

        return () ->
        {
            if (cache[0] == null)
            {
                JsonbDeserializer<?>[] others = all(operationBindingTypes, messageBindingTypes, serverBindingTypes).stream()
                    .filter(deserializer -> deserializer.getClass() != exclude)
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
}
