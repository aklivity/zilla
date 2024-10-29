/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

public class OptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private final Map<String, OptionsConfigAdapterSpi> delegatesByType;
    private ConfigAdapterContext context;

    private OptionsConfigAdapterSpi delegate;

    public OptionsConfigAdapter(
        OptionsConfigAdapterSpi.Kind kind,
        ConfigAdapterContext context)
    {
        this.delegatesByType = toMap(ServiceLoader
            .load(OptionsConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .filter(s -> s.kind() == kind)
            .toList());
        this.context = context;
    }

    public void adaptType(
        String type)
    {
        delegate = delegatesByType.get(type);
        if (delegate != null)
        {
            delegate.adaptContext(context);
        }
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }

    private static Map<String, OptionsConfigAdapterSpi> toMap(
        List<OptionsConfigAdapterSpi> adapters)
    {
        Map<String, OptionsConfigAdapterSpi> adaptersByType = new HashMap<>();
        for (OptionsConfigAdapterSpi adapter : adapters)
        {
            String type = adapter.type();
            Set<String> aliases = adapter.aliases();

            adaptersByType.put(type, adapter);
            for (String alias : aliases)
            {
                adaptersByType.put(alias, adapter);
            }
        }
        return unmodifiableMap(adaptersByType);
    }
}
