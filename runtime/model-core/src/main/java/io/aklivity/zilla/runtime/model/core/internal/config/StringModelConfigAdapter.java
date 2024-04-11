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
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfigBuilder;

public final class StringModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String ENCODING_NAME = "encoding";
    private static final String PATTERN_NAME = "pattern";
    private static final String MAX_NAME = "maxLength";
    private static final String MIN_NAME = "minLength";

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        JsonValue result;
        StringModelConfig options = (StringModelConfig) config;

        if (options.encoding.equals(StringModelConfigBuilder.DEFAULT_ENCODING) &&
            options.pattern == null &&
            options.maxLength == 0 &&
            options.minLength == 0)
        {
            result = Json.createValue(type());
        }
        else
        {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(MODEL_NAME, type());

            if (!options.encoding.equals(StringModelConfigBuilder.DEFAULT_ENCODING))
            {
                builder.add(ENCODING_NAME, options.encoding);
            }

            if (options.pattern != null)
            {
                builder.add(PATTERN_NAME, options.pattern);
            }

            if (options.maxLength != 0)
            {
                builder.add(MAX_NAME, options.maxLength);
            }

            if (options.minLength != 0)
            {
                builder.add(MIN_NAME, options.minLength);
            }

            result = builder.build();
        }
        return result;
    }

    @Override
    public StringModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonValue.ValueType valueType = value.getValueType();
        StringModelConfigBuilder<StringModelConfig> builder = StringModelConfig.builder();
        switch (valueType)
        {
        case STRING:
            break;
        case OBJECT:
            JsonObject object = (JsonObject) value;
            if (object.containsKey(ENCODING_NAME))
            {
                builder.encoding(object.getString(ENCODING_NAME));
            }

            if (object.containsKey(PATTERN_NAME))
            {
                builder.pattern(object.getString(PATTERN_NAME));
            }

            if (object.containsKey(MAX_NAME))
            {
                builder.maxLength(object.getInt(MAX_NAME));
            }

            if (object.containsKey(MIN_NAME))
            {
                builder.minLength(object.getInt(MIN_NAME));
            }
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }
        return builder.build();
    }

    @Override
    public String type()
    {
        return "string";
    }
}
