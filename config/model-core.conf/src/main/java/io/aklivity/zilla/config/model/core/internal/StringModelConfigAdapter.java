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

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.ConfigExtAdapter;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.ValidateConfig;
import io.aklivity.zilla.config.engine.ValidateConfigAdapter;
import io.aklivity.zilla.config.model.core.StringModelConfig;
import io.aklivity.zilla.config.model.core.StringModelConfigBuilder;

public final class StringModelConfigAdapter extends ConfigAdapter.Extensible<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String STRING = "string";
    private static final String ENCODING_NAME = "encoding";
    private static final String PATTERN_NAME = "pattern";
    private static final String MAX_NAME = "maxLength";
    private static final String MIN_NAME = "minLength";
    private static final String VALIDATE_NAME = "validate";

    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

    public StringModelConfigAdapter(
        List<ConfigExtAdapter<ModelConfig>> extensions)
    {
        super(extensions);
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        StringModelConfig options = (StringModelConfig) config;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(MODEL_NAME, STRING);

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

        JsonValue validateJson = validate.adaptToJson(options.validate);
        if (validateJson != null)
        {
            builder.add(VALIDATE_NAME, validateJson);
        }

        injectExtensions(options, builder);

        // no non-default property and no extension present: emit the bare "string" shorthand
        JsonObject object = builder.build();
        return object.size() == 1 ? Json.createValue(STRING) : object;
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

            ValidateConfig validateConfig = validate.adaptFromJsonObject(object);
            builder.validate(validateConfig);

            injectExtensions(object, builder);
            break;
        default:
            throw new IllegalArgumentException("Unexpected type: " + valueType);
        }
        return builder.build();
    }
}
