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
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.config.engine.WithConfigAdapterSpi;

public class WithAdapter implements JsonbAdapter<WithConfig, JsonObject>
{
    private final EngineInfo info;
    private final Map<String, WithConfigAdapterSpi> delegatesByName;

    private JsonbAdapter<WithConfig, JsonObject> delegate;

    public WithAdapter()
    {
        this(null);
    }

    public WithAdapter(
        EngineInfo info)
    {
        this.info = info;
        this.delegatesByName = ServiceLoader
            .load(WithConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(WithConfigAdapterSpi::type, identity()));
    }

    public void adaptType(
        String type)
    {
        BindingInfo binding = info != null ? info.binding(type) : null;
        JsonbAdapter<WithConfig, JsonObject> resolved = binding != null ? binding.with() : null;
        delegate = resolved != null ? resolved : delegatesByName.get(type);
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with) throws Exception
    {
        return delegate != null ? delegate.adaptToJson(with) : null;
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
