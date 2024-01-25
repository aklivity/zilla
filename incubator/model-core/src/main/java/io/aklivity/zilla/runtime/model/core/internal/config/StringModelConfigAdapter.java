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
package io.aklivity.zilla.runtime.model.core.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public final class StringModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String TYPE_NAME = "type";
    private static final String ENCODING_NAME = "encoding";

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        JsonValue result;
        String encoding = ((StringModelConfig) config).encoding;
        if (encoding != null && !encoding.isEmpty() && !encoding.equals(StringModelConfig.DEFAULT_ENCODING))
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
    public StringModelConfig adaptFromJson(
        JsonValue value)
    {
        StringModelConfig result = null;
        if (value instanceof JsonString)
        {
            result = StringModelConfig.builder().build();
        }
        else if (value instanceof JsonObject)
        {
            JsonObject object = (JsonObject) value;
            String encoding = object.containsKey(ENCODING_NAME)
                ? object.getString(ENCODING_NAME)
                : null;
            result = new StringModelConfig(encoding);
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
