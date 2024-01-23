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
package io.aklivity.zilla.runtime.types.core.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConverterConfig;
import io.aklivity.zilla.runtime.engine.config.ConverterConfigAdapterSpi;
import io.aklivity.zilla.runtime.types.core.config.StringConverterConfig;

public final class StringConverterConfigAdapter implements ConverterConfigAdapterSpi, JsonbAdapter<ConverterConfig, JsonValue>
{
    private static final String TYPE_NAME = "type";
    private static final String ENCODING_NAME = "encoding";

    @Override
    public JsonValue adaptToJson(
        ConverterConfig config)
    {
        JsonValue result;
        String encoding = ((StringConverterConfig) config).encoding;
        if (encoding != null && !encoding.isEmpty() && !encoding.equals(StringConverterConfig.DEFAULT_ENCODING))
        {
            JsonObjectBuilder converter = Json.createObjectBuilder();
            converter.add(TYPE_NAME, type());
            converter.add(ENCODING_NAME, encoding);
            result = converter.build();
        }
        else
        {
            result = Json.createValue("string");
        }
        return result;
    }

    @Override
    public StringConverterConfig adaptFromJson(
        JsonValue value)
    {
        StringConverterConfig result = null;
        if (value instanceof JsonString)
        {
            result = StringConverterConfig.builder().build();
        }
        else if (value instanceof JsonObject)
        {
            JsonObject object = (JsonObject) value;
            String encoding = object.containsKey(ENCODING_NAME)
                ? object.getString(ENCODING_NAME)
                : null;
            result = new StringConverterConfig(encoding);
        }
        else
        {
            assert false;
        }
        return result;
    }

    @Override
    public String type()
    {
        return "string";
    }
}
