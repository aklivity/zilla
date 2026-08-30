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
    private static final Map<String, JsonbAdapter<? extends Config, JsonObject>> NO_ITEM_ADAPTERS = Map.of();

    private final Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName;
    private final Map<String, JsonbAdapter<? extends Config, JsonArray>> arrayAdaptersByName;
    private final Map<String, JsonbAdapter<? extends Config, JsonObject>> itemAdaptersByName;

    public ConfigExtAdapter(
        Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName)
    {
        this(adaptersByName, NO_ARRAY_ADAPTERS, NO_ITEM_ADAPTERS);
    }

    // an extension is usually keyed to a JSON object, but some extensions are naturally list-shaped in
    // their entirety (e.g. an ordered list of match rules) with no other property to hang off the same
    // name -- arrayAdaptersByName registers those under a JsonArray-typed adapter instead, resolved and
    // ref-bubbled identically to the object-typed adapters above, just keyed off a JSON array value
    public ConfigExtAdapter(
        Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName,
        Map<String, JsonbAdapter<? extends Config, JsonArray>> arrayAdaptersByName)
    {
        this(adaptersByName, arrayAdaptersByName, NO_ITEM_ADAPTERS);
    }

    // adaptersByName/arrayAdaptersByName bolt an optional named field onto the owner's own JSON object.
    // itemAdaptersByName is a different shape: it registers one additional discriminated variant of a
    // field the owner's own schema already declares as an array of type-selected items (e.g. binding-mcp's
    // search index list) -- keyed by that item's own type value rather than by a field name, and consulted
    // by the owner's own per-item adapter only for type values it does not already recognize itself
    public ConfigExtAdapter(
        Map<String, JsonbAdapter<? extends Config, JsonObject>> adaptersByName,
        Map<String, JsonbAdapter<? extends Config, JsonArray>> arrayAdaptersByName,
        Map<String, JsonbAdapter<? extends Config, JsonObject>> itemAdaptersByName)
    {
        this.adaptersByName = adaptersByName;
        this.arrayAdaptersByName = arrayAdaptersByName;
        this.itemAdaptersByName = itemAdaptersByName;
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

    // dispatch for one discriminated array item this extension recognizes by its own type value;
    // returns null when this extension does not handle that type, so the owner's per-item adapter
    // can fall through to whichever other registered extension (or its own built-in types) does
    @SuppressWarnings("unchecked")
    public JsonObject adaptItemToJson(
        String type,
        Config item)
    {
        JsonbAdapter<? extends Config, JsonObject> adapter = itemAdaptersByName.get(type);
        JsonObject adapted = null;
        if (adapter != null)
        {
            try
            {
                adapted = ((JsonbAdapter<Config, JsonObject>) adapter).adaptToJson(item);
            }
            catch (Exception ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }
        return adapted;
    }

    @SuppressWarnings("unchecked")
    public Config adaptItemFromJson(
        String type,
        JsonObject object)
    {
        JsonbAdapter<? extends Config, JsonObject> adapter = itemAdaptersByName.get(type);
        Config adapted = null;
        if (adapter != null)
        {
            try
            {
                adapted = ((JsonbAdapter<Config, JsonObject>) adapter).adaptFromJson(object);
            }
            catch (Exception ex)
            {
                throw new IllegalArgumentException(ex);
            }
        }
        return adapted;
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
