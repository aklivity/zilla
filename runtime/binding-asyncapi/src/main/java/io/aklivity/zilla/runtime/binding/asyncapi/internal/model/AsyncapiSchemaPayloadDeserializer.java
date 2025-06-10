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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model;

import java.lang.reflect.Type;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

public class AsyncapiSchemaPayloadDeserializer implements JsonbDeserializer<AsyncapiSchemaItem>
{
    @Override
    public AsyncapiSchemaItem deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type rtType) throws JsonbException
    {
        Jsonb jsonb = JsonbBuilder.create();
        JsonObject value = parser.getObject();

        AsyncapiSchemaItem schema = null;

        if (value.containsKey("$ref"))
        {
            AsyncapiSchemaItem asyncapiSchemaItem = new AsyncapiSchemaItem();
            asyncapiSchemaItem.ref = value.getString("$ref");
            schema = asyncapiSchemaItem;
        }
        else if (value.containsKey("type"))
        {
            schema = jsonb.fromJson(value.toString(), AsyncapiSchema.class);
        }
        else if (value.containsKey("schemaFormat"))
        {
            schema = jsonb.fromJson(value.toString(), AsyncapiMultiFormatSchema.class);
        }

        return schema;
    }
}
