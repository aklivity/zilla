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

import static io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig.INT_32;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfigBuilder;

public class Int32ModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String FORMAT_NAME = "format";
    private static final String MAX_NAME = "max";
    private static final String MIN_NAME = "min";
    private static final String EXCLUSIVE_MAX_NAME = "exclusiveMax";
    private static final String EXCLUSIVE_MIN_NAME = "exclusiveMin";
    private static final String MULTIPLE_NAME = "multiple";

    @Override
    public String type()
    {
        return INT_32;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        Int32ModelConfig config = (Int32ModelConfig) options;
        JsonObjectBuilder builder = Json.createObjectBuilder();

        builder.add(MODEL_NAME, INT_32);

        if (!config.format.equals(Int32ModelConfigBuilder.DEFAULT_FORMAT))
        {
            builder.add(FORMAT_NAME, config.format);
        }

        if (config.max != Integer.MAX_VALUE)
        {
            builder.add(MAX_NAME, config.max);
        }

        if (config.min != Integer.MIN_VALUE)
        {
            builder.add(MIN_NAME, config.min);
        }

        if (config.exclusiveMax)
        {
            builder.add(EXCLUSIVE_MAX_NAME, config.exclusiveMax);
        }

        if (config.exclusiveMin)
        {
            builder.add(EXCLUSIVE_MIN_NAME, config.exclusiveMin);
        }

        if (config.multiple != Int32ModelConfigBuilder.DEFAULT_MULTIPLE)
        {
            builder.add(MULTIPLE_NAME, config.multiple);
        }

        return builder.build();
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        Int32ModelConfig result = null;
        if (value instanceof JsonString)
        {
            result = Int32ModelConfig.builder().build();
        }
        else if (value instanceof JsonObject)
        {
            JsonObject object = (JsonObject) value;
            String format = object.containsKey(FORMAT_NAME)
                ? object.getString(FORMAT_NAME)
                : null;

            int max = object.containsKey(MAX_NAME)
                ? object.getInt(MAX_NAME)
                : 0;

            int min = object.containsKey(MIN_NAME)
                ? object.getInt(MIN_NAME)
                : 0;

            boolean exclusiveMax = object.containsKey(EXCLUSIVE_MAX_NAME)
                ? object.getBoolean(EXCLUSIVE_MAX_NAME)
                : false;

            boolean exclusiveMin = object.containsKey(EXCLUSIVE_MIN_NAME)
                ? object.getBoolean(EXCLUSIVE_MIN_NAME)
                : false;

            int multiple = object.containsKey(MULTIPLE_NAME)
                ? object.getInt(MULTIPLE_NAME)
                : 0;

            result = new Int32ModelConfig(format, max, min, exclusiveMax, exclusiveMin, multiple);
        }
        else
        {
            assert false;
        }
        return result;
    }
}
