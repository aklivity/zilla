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

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

public final class OpenapiDeserializer implements JsonbDeserializer<Openapi>
{
    private final Map<String, Class<?>> extensionTypes;
    private final Supplier<Jsonb> plain;

    public OpenapiDeserializer(
        Map<String, Class<?>> extensionTypes)
    {
        this.extensionTypes = extensionTypes;
        this.plain = OpenapiDeserializers.plain(extensionTypes, OpenapiDeserializer.class);
    }

    @Override
    public Openapi deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        JsonObject object = parser.getObject();
        Openapi model = plain.get().fromJson(object.toString(), Openapi.class);

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
        model.extensions = extensions;

        return model;
    }
}
