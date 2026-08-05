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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.JsonbDeserializer;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiExtension;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public final class AsyncapiDeserializers
{
    private AsyncapiDeserializers()
    {
    }

    public static List<JsonbDeserializer<?>> all(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<String, Class<?>> channelBindingTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        return List.of(
            new AsyncapiDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiServerDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiServerVariableDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiChannelDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiOperationDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiMessageDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiTraitDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiParameterDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiComponentsDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiSchemaDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiMultiFormatSchemaDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiSchemaItemDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiSecuritySchemeDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiCorrelationIdDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes),
            new AsyncapiReplyDeserializer(
                operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                prefixExtensionTypes));
    }

    static Supplier<Jsonb> plain(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<String, Class<?>> channelBindingTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes,
        Class<?>... excludes)
    {
        Jsonb[] cache = new Jsonb[1];
        Set<Class<?>> excluded = Set.of(excludes);

        return () ->
        {
            if (cache[0] == null)
            {
                JsonbDeserializer<?>[] others = all(
                    operationBindingTypes, messageBindingTypes, serverBindingTypes, channelBindingTypes, extensionTypes,
                    prefixExtensionTypes)
                        .stream()
                        .filter(deserializer -> !excluded.contains(deserializer.getClass()))
                        .toArray(JsonbDeserializer[]::new);
                cache[0] = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withDeserializers(others))
                    .withProvider(YamlJson.provider())
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
                    Object binding = Map.class.equals(bindingType)
                        ? toPlainValue(entry.getValue())
                        : plain.fromJson(entry.getValue().toString(), bindingType);
                    bindings.put(entry.getKey(), binding);
                }
            }
        }

        return bindings;
    }

    static Map<String, Object> extensions(
        JsonObject object,
        AsyncapiExtension.Scope scope,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes,
        Supplier<Jsonb> plain)
    {
        Map<String, Class<?>> scopedExtensionTypes = extensionTypes.getOrDefault(scope, emptyMap());
        Map<String, Class<?>> scopedPrefixExtensionTypes = prefixExtensionTypes.getOrDefault(scope, emptyMap());

        Map<String, Object> extensions = null;

        for (String name : object.keySet())
        {
            if (name.startsWith("x-"))
            {
                Class<?> extensionType = scopedExtensionTypes.get(name);
                if (extensionType != null)
                {
                    if (extensions == null)
                    {
                        extensions = new LinkedHashMap<>();
                    }
                    Object extension = Map.class.equals(extensionType)
                        ? toPlainValue(object.get(name))
                        : plain.get().fromJson(object.get(name).toString(), extensionType);
                    extensions.put(name, extension);
                }
            }
        }

        for (Map.Entry<String, Class<?>> prefixExtensionType : scopedPrefixExtensionTypes.entrySet())
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
                        aggregate = YamlJson.provider().createObjectBuilder();
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
                JsonObject aggregated = aggregate.build();
                Class<?> prefixExtensionClass = prefixExtensionType.getValue();
                Object extension = Map.class.equals(prefixExtensionClass)
                    ? toPlainValue(aggregated)
                    : plain.get().fromJson(aggregated.toString(), prefixExtensionClass);
                extensions.put(registeredName, extension);
            }
        }

        return extensions;
    }

    public static Object toPlainValue(
        JsonValue value)
    {
        Object result;

        switch (value.getValueType())
        {
        case OBJECT:
            Map<String, Object> object = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : value.asJsonObject().entrySet())
            {
                object.put(entry.getKey(), toPlainValue(entry.getValue()));
            }
            result = object;
            break;
        case ARRAY:
            List<Object> array = new ArrayList<>();
            for (JsonValue element : (JsonArray) value)
            {
                array.add(toPlainValue(element));
            }
            result = array;
            break;
        case STRING:
            result = ((JsonString) value).getString();
            break;
        case NUMBER:
            result = toPlainNumber((JsonNumber) value);
            break;
        case TRUE:
            result = Boolean.TRUE;
            break;
        case FALSE:
            result = Boolean.FALSE;
            break;
        case NULL:
        default:
            result = null;
            break;
        }

        return result;
    }

    private static Object toPlainNumber(
        JsonNumber number)
    {
        Object result;

        if (!number.isIntegral())
        {
            result = number.doubleValue();
        }
        else if (number.bigIntegerValue().bitLength() < 32)
        {
            result = number.intValue();
        }
        else
        {
            result = number.longValue();
        }

        return result;
    }
}
