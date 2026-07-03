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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

public final class AsyncapiSchemaDeserializer implements JsonbDeserializer<AsyncapiSchema>
{
    private final Map<String, Class<?>> extensionTypes;
    private final Map<String, Class<?>> prefixExtensionTypes;
    private final Supplier<Jsonb> plain;

    public AsyncapiSchemaDeserializer(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes)
    {
        this.extensionTypes = extensionTypes;
        this.prefixExtensionTypes = prefixExtensionTypes;
        this.plain = AsyncapiDeserializers.plain(
            operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes,
            AsyncapiSchemaDeserializer.class);
    }

    @Override
    public AsyncapiSchema deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        return bind(parser.getObject(), extensionTypes, prefixExtensionTypes, plain);
    }

    static AsyncapiSchema bind(
        JsonObject object,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes,
        Supplier<Jsonb> plain)
    {
        AsyncapiSchema model = plain.get().fromJson(object.toString(), AsyncapiSchema.class);

        if (model.items != null)
        {
            model.items = bind(object.getJsonObject("items"), extensionTypes, prefixExtensionTypes, plain);
        }

        if (model.schema != null)
        {
            model.schema = bind(object.getJsonObject("schema"), extensionTypes, prefixExtensionTypes, plain);
        }

        if (model.properties != null)
        {
            JsonObject properties = object.getJsonObject("properties");
            model.properties.replaceAll((name, property) ->
                bind(properties.getJsonObject(name), extensionTypes, prefixExtensionTypes, plain));
        }

        if (model.oneOf != null)
        {
            bindAll(model.oneOf, object.getJsonArray("oneOf"), extensionTypes, prefixExtensionTypes, plain);
        }

        if (model.allOf != null)
        {
            bindAll(model.allOf, object.getJsonArray("allOf"), extensionTypes, prefixExtensionTypes, plain);
        }

        if (model.anyOf != null)
        {
            bindAll(model.anyOf, object.getJsonArray("anyOf"), extensionTypes, prefixExtensionTypes, plain);
        }

        model.extensions = AsyncapiDeserializers.extensions(object, extensionTypes, prefixExtensionTypes, plain);

        return model;
    }

    private static void bindAll(
        List<AsyncapiSchema> schemas,
        JsonArray objects,
        Map<String, Class<?>> extensionTypes,
        Map<String, Class<?>> prefixExtensionTypes,
        Supplier<Jsonb> plain)
    {
        for (int i = 0; i < schemas.size(); i++)
        {
            schemas.set(i, bind(objects.getJsonObject(i), extensionTypes, prefixExtensionTypes, plain));
        }
    }
}
