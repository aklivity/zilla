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
import io.aklivity.zilla.config.model.core.BytesModelConfig;
import io.aklivity.zilla.config.model.core.BytesModelConfigBuilder;

public final class BytesModelConfigAdapter extends ConfigAdapter.Extensible<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String BYTES = "bytes";
    private static final String VALIDATE_NAME = "validate";

    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

    public BytesModelConfigAdapter(
        List<ConfigExtAdapter<ModelConfig>> extensions)
    {
        super(extensions);
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig config)
    {
        BytesModelConfig options = (BytesModelConfig) config;
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(MODEL_NAME, BYTES);

        JsonValue validateJson = validate.adaptToJson(options.validate);
        if (validateJson != null)
        {
            builder.add(VALIDATE_NAME, validateJson);
        }

        injectExtensions(options, builder);

        // no validate override and no extension present: emit the bare "bytes" shorthand
        JsonObject object = builder.build();
        return object.size() == 1 ? Json.createValue(BYTES) : object;
    }

    @Override
    public BytesModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonValue.ValueType valueType = value.getValueType();
        BytesModelConfigBuilder<BytesModelConfig> builder = BytesModelConfig.builder();
        switch (valueType)
        {
        case STRING:
            break;
        case OBJECT:
            JsonObject object = (JsonObject) value;

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
