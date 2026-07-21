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

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiExtension;

public final class AsyncapiSchemaItemDeserializer implements JsonbDeserializer<AsyncapiSchemaItem>
{
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes;
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes;
    private final Supplier<Jsonb> plain;

    public AsyncapiSchemaItemDeserializer(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        this.extensionTypes = extensionTypes;
        this.prefixExtensionTypes = prefixExtensionTypes;
        this.plain = AsyncapiDeserializers.plain(
            operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes,
            AsyncapiSchemaItemDeserializer.class, AsyncapiSchemaDeserializer.class, AsyncapiMultiFormatSchemaDeserializer.class);
    }

    @Override
    public AsyncapiSchemaItem deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        JsonObject object = parser.getObject();

        AsyncapiSchemaItem item = null;

        if (object.containsKey("$ref"))
        {
            AsyncapiSchemaItem schemaItem = new AsyncapiSchemaItem();
            schemaItem.ref = object.getString("$ref");
            item = schemaItem;
        }
        else if (object.containsKey("type"))
        {
            item = AsyncapiSchemaDeserializer.bind(object, extensionTypes, prefixExtensionTypes, plain);
        }
        else
        {
            item = AsyncapiMultiFormatSchemaDeserializer.bind(object, extensionTypes, prefixExtensionTypes, plain);
        }

        return item;
    }
}
