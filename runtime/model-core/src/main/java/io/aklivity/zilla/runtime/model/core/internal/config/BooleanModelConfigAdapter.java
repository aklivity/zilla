/*
 * Copyright 2021-2024 Aklivity Inc
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
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.BooleanModelConfig;

public class BooleanModelConfigAdapter implements ModelConfigAdapterSpi, JsonbAdapter<ModelConfig, JsonValue>
{
    @Override
    public String type()
    {
        return BOOLEAN;
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        return Json.createValue(type());
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        return BooleanModelConfig.builder().build();
    }
}
