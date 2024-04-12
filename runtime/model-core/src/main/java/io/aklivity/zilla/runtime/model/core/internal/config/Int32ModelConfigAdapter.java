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
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.RangeConfig;

public class Int32ModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String FORMAT_NAME = "format";
    private static final String MULTIPLE_NAME = "multiple";
    private static final String RANGE_NAME = "range";

    private final RangeConfigAdapter adapter = new RangeConfigAdapter();

    @Override
    public String type()
    {
        return INT_32;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        JsonValue result;
        Int32ModelConfig config = (Int32ModelConfig) options;

        if (config.format.equals(Int32ModelConfigBuilder.DEFAULT_FORMAT) &&
            config.max == Integer.MAX_VALUE &&
            config.min == Integer.MIN_VALUE &&
            !config.exclusiveMax &&
            !config.exclusiveMin &&
            config.multiple == Int32ModelConfigBuilder.DEFAULT_MULTIPLE)
        {
            result = Json.createValue(type());
        }
        else
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();

            builder.add(MODEL_NAME, INT_32);

            if (!config.format.equals(Int32ModelConfigBuilder.DEFAULT_FORMAT))
            {
                builder.add(FORMAT_NAME, config.format);
            }

            String max = config.max != Integer.MAX_VALUE ? String.valueOf(config.max) : null;
            String min = config.min != Integer.MIN_VALUE ? String.valueOf(config.min) : null;
            boolean exclusiveMax = config.exclusiveMax;
            boolean exclusiveMin = config.exclusiveMin;
            RangeConfig range = new RangeConfig(max, min, exclusiveMax, exclusiveMin);
            builder.add(RANGE_NAME, adapter.adaptToString(range));

            if (config.multiple != Int32ModelConfigBuilder.DEFAULT_MULTIPLE)
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
        ValueType valueType = value.getValueType();
        Int32ModelConfigBuilder<Int32ModelConfig> builder = Int32ModelConfig.builder();

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

            if (object.containsKey(RANGE_NAME))
            {
                RangeConfig range = adapter.adaptFromString(object.getString(RANGE_NAME));
                builder.exclusiveMin(range.exclusiveMin);
                builder.exclusiveMax(range.exclusiveMax);
                if (range.min != null)
                {
                    builder.min(Integer.parseInt(range.min));
                }

                if (range.max != null)
                {
                    builder.max(Integer.parseInt(range.max));
                }
            }

            if (object.containsKey(MULTIPLE_NAME))
            {
                builder.multiple(object.getInt(MULTIPLE_NAME));
            }
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }

        return builder.build();
    }
}
