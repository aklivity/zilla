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

import static io.aklivity.zilla.runtime.model.core.config.FloatModelConfig.FLOAT;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfigBuilder;

public class FloatModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
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
        return FLOAT;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        FloatModelConfig config = (FloatModelConfig) options;
        JsonObjectBuilder builder = Json.createObjectBuilder();

        builder.add(MODEL_NAME, type());

        if (!config.format.equals(FloatModelConfigBuilder.DEFAULT_FORMAT))
        {
            builder.add(FORMAT_NAME, config.format);
        }

        if (config.max != Float.POSITIVE_INFINITY)
        {
            builder.add(MAX_NAME, config.max);
        }

        if (config.min != Float.NEGATIVE_INFINITY)
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

        if (config.multiple != null)
        {
            builder.add(MULTIPLE_NAME, config.multiple);
        }

        return builder.build();
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonValue.ValueType valueType = value.getValueType();
        FloatModelConfigBuilder<FloatModelConfig> builder = FloatModelConfig.builder();

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
                builder.max(object.getJsonNumber(MAX_NAME).bigDecimalValue().floatValue());
            }

            if (object.containsKey(MIN_NAME))
            {
                builder.min(object.getJsonNumber(MIN_NAME).bigDecimalValue().floatValue());
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
                builder.multiple(object.getJsonNumber(MULTIPLE_NAME).bigDecimalValue().floatValue());
            }
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }
        return builder.build();
    }
}
