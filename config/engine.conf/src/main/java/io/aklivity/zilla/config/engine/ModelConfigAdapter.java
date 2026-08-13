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
package io.aklivity.zilla.config.engine;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.factory.Factory;

public final class ModelConfigAdapter extends ConfigAdapter<ModelConfig, JsonValue>
{
    private static final String MODEL_NAME = "model";

    private final Map<String, ConfigAdapter<ModelConfig, JsonValue>> delegatesByName;
    private ConfigAdapter<ModelConfig, JsonValue> delegate;

    public ModelConfigAdapter()
    {
        delegatesByName = Factory.instantiate(ServiceLoader.load(ModelInfo.class))
            .stream()
            .collect(toMap(ModelInfo::type, ModelInfo::adapter));
    }

    public void adaptType(
        String type)
    {
        delegate = delegatesByName.get(type);
    }

    @Override
    public JsonValue adaptToJson(
        ModelConfig options)
    {
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public ModelConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = null;
        if (value instanceof JsonString)
        {
            object = Json.createObjectBuilder()
                .add(MODEL_NAME, ((JsonString) value).getString())
                .build();
        }
        else if (value instanceof JsonObject)
        {
            object = (JsonObject) value;
        }
        else
        {
            assert false;
        }

        String type = object.containsKey(MODEL_NAME)
                ? object.getString(MODEL_NAME)
                : null;

        adaptType(type);

        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
