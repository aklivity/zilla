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

import static io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig.INT_64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfigBuilder;

public class Int64ModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
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
        return INT_64;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        JsonValue result;
        Int64ModelConfig config = (Int64ModelConfig) options;

        if (config.format.equals(Int64ModelConfigBuilder.DEFAULT_FORMAT) &&
            config.max == Long.MAX_VALUE &&
            config.min == Long.MIN_VALUE &&
            !config.exclusiveMax &&
            !config.exclusiveMin &&
            config.multiple == Int64ModelConfigBuilder.DEFAULT_MULTIPLE)
        {
            result = Json.createValue(type());
        }
        else
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();

            builder.add(MODEL_NAME, INT_64);

            if (!config.format.equals(Int64ModelConfigBuilder.DEFAULT_FORMAT))
            {
                builder.add(FORMAT_NAME, config.format);
            }

            if (config.max != Long.MAX_VALUE)
            {
                builder.add(MAX_NAME, config.max);
            }

            if (config.min != Long.MIN_VALUE)
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

            if (config.multiple != Int64ModelConfigBuilder.DEFAULT_MULTIPLE)
            {
                builder.add(MULTIPLE_NAME, config.multiple);
            }

            result = builder.build();
        }

        return result;
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonValue.ValueType valueType = value.getValueType();
        Int64ModelConfigBuilder<Int64ModelConfig> builder = Int64ModelConfig.builder();

        switch (valueType)
        {
        case STRING:
            break;
        case OBJECT:
            JsonObject object = (JsonObject) value;
            if (object.containsKey(FORMAT_NAME))
            {
                builder.format(object.getString(FORMAT_NAME));
            }

            if (object.containsKey(MAX_NAME))
            {
                builder.max(object.getJsonNumber(MAX_NAME).longValue());
            }

            if (object.containsKey(MIN_NAME))
            {
                builder.min(object.getJsonNumber(MIN_NAME).longValue());
            }

            if (object.containsKey(EXCLUSIVE_MAX_NAME))
            {
                builder.exclusiveMax(object.getBoolean(EXCLUSIVE_MAX_NAME));
            }

            if (object.containsKey(EXCLUSIVE_MIN_NAME))
            {
                builder.exclusiveMin(object.getBoolean(EXCLUSIVE_MIN_NAME));
            }

            if (object.containsKey(MULTIPLE_NAME))
            {
                builder.multiple(object.getJsonNumber(MULTIPLE_NAME).longValue());
            }
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }
        return builder.build();
    }
}
