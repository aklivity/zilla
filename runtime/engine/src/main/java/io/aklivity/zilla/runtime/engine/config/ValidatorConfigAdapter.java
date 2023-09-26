/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class ValidatorConfigAdapter implements JsonbAdapter<ValidatorConfig, JsonValue>
{
    private static final String TYPE_NAME = "type";

    private final Map<String, ValidatorConfigAdapterSpi> delegatesByName;
    private ValidatorConfigAdapterSpi delegate;

    public ValidatorConfigAdapter()
    {
        delegatesByName = ServiceLoader
            .load(ValidatorConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(ValidatorConfigAdapterSpi::type, identity()));
    }

    public void adaptType(
        String type)
    {
        delegate = delegatesByName.get(type);
    }

    @Override
    public JsonValue adaptToJson(
        ValidatorConfig options)
    {
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public ValidatorConfig adaptFromJson(
        JsonValue value)
    {
        JsonObject object = null;
        if (value instanceof JsonString)
        {
            object = Json.createObjectBuilder()
                .add(TYPE_NAME, ((JsonString) value).getString())
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

        String type = object.containsKey(TYPE_NAME)
                ? object.getString(TYPE_NAME)
                : null;

        adaptType(type);

        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
