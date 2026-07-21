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
package io.aklivity.zilla.runtime.common.openapi.model;

import static java.util.Collections.emptyMap;

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

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiExtension;

public final class OpenapiSchemaDeserializer implements JsonbDeserializer<OpenapiSchema>
{
    private final Map<String, Class<?>> extensionTypes;
    private final Map<String, Class<?>> prefixExtensionTypes;
    private final Supplier<Jsonb> plain;

    public OpenapiSchemaDeserializer(
        Map<OpenapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<OpenapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        this.extensionTypes = extensionTypes.getOrDefault(OpenapiExtension.Scope.SCHEMA, emptyMap());
        this.prefixExtensionTypes = prefixExtensionTypes.getOrDefault(OpenapiExtension.Scope.SCHEMA, emptyMap());
        this.plain = OpenapiDeserializers.plain(extensionTypes, prefixExtensionTypes, OpenapiSchemaDeserializer.class);
    }

    @Override
    public OpenapiSchema deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        return bind(parser.getObject());
    }

    private OpenapiSchema bind(
        JsonObject object)
    {
        OpenapiSchema model = plain.get().fromJson(object.toString(), OpenapiSchema.class);

        if (model.items != null)
        {
            model.items = bind(object.getJsonObject("items"));
        }

        if (model.schema != null)
        {
            model.schema = bind(object.getJsonObject("schema"));
        }

        if (model.properties != null)
        {
            JsonObject properties = object.getJsonObject("properties");
            model.properties.replaceAll((name, property) -> bind(properties.getJsonObject(name)));
        }

        if (model.oneOf != null)
        {
            bindAll(model.oneOf, object.getJsonArray("oneOf"));
        }

        if (model.allOf != null)
        {
            bindAll(model.allOf, object.getJsonArray("allOf"));
        }

        if (model.anyOf != null)
        {
            bindAll(model.anyOf, object.getJsonArray("anyOf"));
        }

        model.extensions = OpenapiDeserializers.extensions(object, extensionTypes, prefixExtensionTypes, plain);

        return model;
    }

    private void bindAll(
        List<OpenapiSchema> schemas,
        JsonArray objects)
    {
        for (int i = 0; i < schemas.size(); i++)
        {
            schemas.set(i, bind(objects.getJsonObject(i)));
        }
    }
}
