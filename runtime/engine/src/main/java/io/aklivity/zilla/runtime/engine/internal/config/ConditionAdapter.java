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
package io.aklivity.zilla.runtime.engine.internal.config;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi;

public class ConditionAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private final Map<String, ConditionConfigAdapterSpi> delegatesByName;

    private ConditionConfigAdapterSpi delegate;

    public ConditionAdapter()
    {
        delegatesByName = ServiceLoader
            .load(ConditionConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(ConditionConfigAdapterSpi::type, identity()));
    }

    public void adaptType(
        String type)
    {
        delegate = delegatesByName.get(type);
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition)
    {
        return delegate != null ? delegate.adaptToJson(condition) : null;
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object)
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
