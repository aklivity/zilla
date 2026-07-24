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
package io.aklivity.zilla.config.model.core.internal;

import static io.aklivity.zilla.config.model.core.FloatModelConfig.FLOAT;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ModelConfigAdapterSpi;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateConfigAdapter;
import io.aklivity.zilla.config.model.core.FloatModelConfig;
import io.aklivity.zilla.config.model.core.FloatModelConfigBuilder;
import io.aklivity.zilla.config.model.core.RangeConfig;

public class FloatModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String FORMAT_NAME = "format";
    private static final String MULTIPLE_NAME = "multiple";
    private static final String RANGE_NAME = "range";
    private static final String VALIDATE_NAME = "validate";

    private final RangeConfigAdapter adapter = new RangeConfigAdapter();
    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

    @Override
    public String type()
    {
        return FLOAT;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        JsonValue result;
        FloatModelConfig config = (FloatModelConfig) options;
        JsonValue validateJson = validate.adaptToJson(config.validate);

        if (config.format.equals(FloatModelConfigBuilder.DEFAULT_FORMAT) &&
            config.max == Float.POSITIVE_INFINITY &&
            config.min == Float.NEGATIVE_INFINITY &&
            !config.exclusiveMax &&
            !config.exclusiveMin &&
            config.multiple == null &&
            validateJson == null)
        {
            result = Json.createValue(type());
        }
        else
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(MODEL_NAME, type());

            if (!config.format.equals(FloatModelConfigBuilder.DEFAULT_FORMAT))
            {
                builder.add(FORMAT_NAME, config.format);
            }

            String max = config.max != Float.POSITIVE_INFINITY ? String.valueOf(config.max) : null;
            String min = config.min != Float.NEGATIVE_INFINITY ? String.valueOf(config.min) : null;
            boolean exclusiveMax = config.exclusiveMax;
            boolean exclusiveMin = config.exclusiveMin;
            RangeConfig range = RangeConfig.builder()
                .max(max)
                .min(min)
                .exclusiveMax(exclusiveMax)
                .exclusiveMin(exclusiveMin)
                .build();
            builder.add(RANGE_NAME, adapter.adaptToString(range));

            if (config.multiple != null)
            {
                builder.add(MULTIPLE_NAME, config.multiple);
            }

            if (validateJson != null)
            {
                builder.add(VALIDATE_NAME, validateJson);
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

            if (object.containsKey(RANGE_NAME))
            {
                RangeConfig range = adapter.adaptFromString(object.getString(RANGE_NAME));
                builder.exclusiveMin(range.exclusiveMin);
                builder.exclusiveMax(range.exclusiveMax);
                if (range.min != null)
                {
                    builder.min(Float.parseFloat(range.min));
                }

                if (range.max != null)
                {
                    builder.max(Float.parseFloat(range.max));
                }
            }

            if (object.containsKey(MULTIPLE_NAME))
            {
                builder.multiple(object.getJsonNumber(MULTIPLE_NAME).bigDecimalValue().floatValue());
            }

            ValidateConfig validateConfig = validate.adaptFromJsonObject(object);
            builder.validate(validateConfig);
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }
        return builder.build();
    }
}
