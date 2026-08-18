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

import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

public final class ConfigExtAdapter<T extends Config.Extensible>
{
    private static final Map<String, JsonbAdapter<? extends Config, JsonArray>> NO_ARRAY_ADAPTERS = Map.of();

    private final Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName;
    private final Map<String, JsonbAdapter<? extends Config, JsonArray>> arrayAdaptersByName;

    public ConfigExtAdapter(
        Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName)
    {
        this(adaptersByName, NO_ARRAY_ADAPTERS);
    }

    // an extension is usually keyed to a JSON object, but some extensions are naturally list-shaped in
    // their entirety (e.g. an ordered list of match rules) with no other property to hang off the same
    // name -- arrayAdaptersByName registers those under a JsonArray-typed adapter instead, resolved and
    // ref-bubbled identically to the object-typed adapters above, just keyed off a JSON array value
    public ConfigExtAdapter(
        Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName,
        Map<String, JsonbAdapter<? extends Config, JsonArray>> arrayAdaptersByName)
    {
        this.adaptersByName = adaptersByName;
        this.arrayAdaptersByName = arrayAdaptersByName;
    }

    // config need only be Extensible, not specifically T: adaptFromJson below is already generic over any
    // Extensible builder, since the extensions here are name-keyed rather than tied to one enclosing type
    @SuppressWarnings("unchecked")
    public void adaptToJson(
        Config.Extensible config,
        JsonObjectBuilder object)
    {
        adaptersByName.forEach((name, adapter) ->
        {
            Config extension = config.ext(name, Config.class);
            if (extension != null)
            {
                try
                {
                    object.add(name, ((JsonbAdapter<Config, JsonObject>) adapter).adaptToJson(extension));
                }
                catch (Exception ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        });

        arrayAdaptersByName.forEach((name, adapter) ->
        {
            Config extension = config.ext(name, Config.class);
            if (extension != null)
            {
                try
                {
                    object.add(name, ((JsonbAdapter<Config, JsonArray>) adapter).adaptToJson(extension));
                }
                catch (Exception ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        });
    }

    public <B extends ConfigBuilder.Extensible<?, B>> B adaptFromJson(
        JsonObject object,
        B builder)
    {
        for (Map.Entry<String, JsonbAdapter<? extends Config, JsonObject>> entry : adaptersByName.entrySet())
        {
            String name = entry.getKey();
            if (object.containsKey(name))
            {
                try
                {
                    Config extension = entry.getValue().adaptFromJson(object.getJsonObject(name));
                    builder = adaptRef(builder, name, extension);
                }
                catch (Exception ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }

        for (Map.Entry<String, JsonbAdapter<? extends Config, JsonArray>> entry : arrayAdaptersByName.entrySet())
        {
            String name = entry.getKey();
            if (object.containsKey(name))
            {
                try
                {
                    Config extension = entry.getValue().adaptFromJson(object.getJsonArray(name));
                    builder = adaptRef(builder, name, extension);
                }
                catch (Exception ex)
                {
                    throw new IllegalArgumentException(ex);
                }
            }
        }

        return builder;
    }

    private static <B extends ConfigBuilder.Extensible<?, B>> B adaptRef(
        B builder,
        String name,
        Config extension)
    {
        B adapted = builder.ext(name, extension);
        if (extension instanceof Config.Extensible extensible)
        {
            for (NamedConfig ref : extensible.refs())
            {
                adapted = adapted.ref(ref);
            }
        }
        return adapted;
    }
}
