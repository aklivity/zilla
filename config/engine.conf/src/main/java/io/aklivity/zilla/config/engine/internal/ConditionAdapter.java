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
package io.aklivity.zilla.config.engine.internal;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.BindingInfo;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfigAdapterSpi;
import io.aklivity.zilla.config.engine.EngineInfo;

public class ConditionAdapter implements JsonbAdapter<ConditionConfig, JsonObject>
{
    private final EngineInfo info;
    private final Map<String, ConditionConfigAdapterSpi> delegatesByName;

    private JsonbAdapter<ConditionConfig, JsonObject> delegate;

    public ConditionAdapter()
    {
        this(null);
    }

    public ConditionAdapter(
        EngineInfo info)
    {
        this.info = info;
        this.delegatesByName = ServiceLoader
            .load(ConditionConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(ConditionConfigAdapterSpi::type, identity()));
    }

    public void adaptType(
        String type)
    {
        BindingInfo binding = info != null ? info.binding(type) : null;
        delegate = binding != null ? binding.condition() : delegatesByName.get(type);
    }

    @Override
    public JsonObject adaptToJson(
        ConditionConfig condition) throws Exception
    {
        return delegate != null ? delegate.adaptToJson(condition) : null;
    }

    @Override
    public ConditionConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
