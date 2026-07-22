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
package io.aklivity.zilla.runtime.model.core.internal.config;

import static io.aklivity.zilla.runtime.model.core.config.BooleanModelConfig.BOOLEAN;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.ValidateConfigAdapter;
import io.aklivity.zilla.runtime.model.core.config.BooleanModelConfig;
import io.aklivity.zilla.runtime.model.core.config.BooleanModelConfigBuilder;

public class BooleanModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";
    private static final String VALIDATE_NAME = "validate";

    private final ValidateConfigAdapter validate = new ValidateConfigAdapter();

    @Override
    public String type()
    {
        return BOOLEAN;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        BooleanModelConfig config = (BooleanModelConfig) options;
        JsonValue result;
        JsonValue validateJson = validate.adaptToJson(config.validate);
        if (validateJson != null)
        {
            result = Json.createObjectBuilder()
                .add(MODEL_NAME, type())
                .add(VALIDATE_NAME, validateJson)
                .build();
        }
        else
        {
            result = Json.createValue(type());
        }
        return result;
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        BooleanModelConfigBuilder<BooleanModelConfig> builder = BooleanModelConfig.builder();
        if (value instanceof JsonObject object)
        {
            builder.validate(validate.adaptFromJsonObject(object));
        }
        return builder.build();
    }
}
