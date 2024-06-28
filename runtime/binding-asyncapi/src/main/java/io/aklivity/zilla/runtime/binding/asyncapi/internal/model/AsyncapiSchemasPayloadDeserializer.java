/*
 * Copyright 2021-2023 Aklivity Inc
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
import java.util.HashMap;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

public class AsyncapiSchemasPayloadDeserializer  implements JsonbDeserializer<Map<String, AsyncapiSchemaItem>>
{

    @Override
    public Map<String, AsyncapiSchemaItem> deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type rtType)
    {
        Map<String, AsyncapiSchemaItem> result = new HashMap<>();
        Jsonb jsonb = JsonbBuilder.create();

        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME)
            {
                AsyncapiSchemaItem value;
                String key = parser.getString();
                parser.next();
                JsonObject valueObject = parser.getObject();
                if (valueObject.containsKey("type"))
                {
                    value = jsonb.fromJson(valueObject.toString(), AsyncapiSchema.class);
                }
                else
                {
                    value = jsonb.fromJson(valueObject.toString(), AsyncapiMultiFormatSchema.class);
                }
                result.put(key, value);
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                break;
            }
        }
        return result;
    }
}
