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

import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

public class OptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private final Function<String, ? extends OptionsInfo> infoLookup;
    private final Map<String, OptionsConfigAdapterSpi> delegatesByType;

    private JsonbAdapter<OptionsConfig, JsonObject> delegate;

    public OptionsConfigAdapter(
        OptionsConfigAdapterSpi.Kind kind)
    {
        this(kind, null);
    }

    public OptionsConfigAdapter(
        OptionsConfigAdapterSpi.Kind kind,
        Function<String, ? extends OptionsInfo> infoLookup)
    {
        this.infoLookup = infoLookup;
        this.delegatesByType = toMap(ServiceLoader
            .load(OptionsConfigAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .filter(s -> s.kind() == kind)
            .toList());
    }

    public void adaptType(
        String type)
    {
        OptionsInfo info = infoLookup != null ? infoLookup.apply(type) : null;
        delegate = info != null ? info.options() : delegatesByType.get(type);
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options) throws Exception
    {
        return delegate != null ? delegate.adaptToJson(options) : null;
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object) throws Exception
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
