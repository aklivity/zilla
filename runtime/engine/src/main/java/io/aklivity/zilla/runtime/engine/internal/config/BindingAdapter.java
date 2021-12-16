/*
 * Copyright 2021-2021 Aklivity Inc.
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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.NamespacedRef;
import io.aklivity.zilla.runtime.engine.config.Options;
import io.aklivity.zilla.runtime.engine.config.OptionsAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.Role;
import io.aklivity.zilla.runtime.engine.config.Route;

public class BindingAdapter implements JsonbAdapter<Binding, JsonObject>
{
    private static final String VAULT_NAME = "vault";
    private static final String ENTRY_NAME = "entry";
    private static final String EXIT_NAME = "exit";
    private static final String TYPE_NAME = "type";
    private static final String KIND_NAME = "kind";
    private static final String OPTIONS_NAME = "options";
    private static final String ROUTES_NAME = "routes";

    private static final List<Route> ROUTES_DEFAULT = emptyList();

    private final RoleAdapter role;
    private final RouteAdapter route;
    private final OptionsAdapter options;

    public BindingAdapter()
    {
        this.role = new RoleAdapter();
        this.route = new RouteAdapter();
        this.options = new OptionsAdapter(OptionsAdapterSpi.Kind.BINDING);
    }

    @Override
    public JsonObject adaptToJson(
        Binding binding)
    {
        route.adaptType(binding.type);
        options.adaptType(binding.type);

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (binding.vault != null)
        {
            // TODO: qualified name format
            object.add(VAULT_NAME, binding.vault.name);
        }

        if (binding.entry != null)
        {
            object.add(ENTRY_NAME, binding.entry);
        }

        object.add(TYPE_NAME, binding.type);

        object.add(KIND_NAME, role.adaptToJson(binding.kind));

        if (binding.options != null)
        {
            object.add(OPTIONS_NAME, options.adaptToJson(binding.options));
        }

        if (!ROUTES_DEFAULT.equals(binding.routes))
        {
            JsonArrayBuilder routes = Json.createArrayBuilder();
            binding.routes.forEach(r -> routes.add(route.adaptToJson(r)));
            object.add(ROUTES_NAME, routes);
        }

        if (binding.exit != null)
        {
            object.add(EXIT_NAME, binding.exit.exit);
        }

        return object.build();
    }

    @Override
    public Binding adaptFromJson(
        JsonObject object)
    {
        String type = object.getString(TYPE_NAME);

        route.adaptType(type);
        options.adaptType(type);

        NamespacedRef vault = object.containsKey(VAULT_NAME)
                ? NamespacedRef.of(object.getString(VAULT_NAME))
                : null;
        String entry = object.containsKey(ENTRY_NAME) ? object.getString(ENTRY_NAME) : null;
        Role kind = role.adaptFromJson(object.getJsonString(KIND_NAME));
        Options opts = object.containsKey(OPTIONS_NAME) ?
                options.adaptFromJson(object.getJsonObject(OPTIONS_NAME)) :
                null;
        MutableInteger order = new MutableInteger();
        List<Route> routes = object.containsKey(ROUTES_NAME)
                ? object.getJsonArray(ROUTES_NAME)
                    .stream()
                    .map(JsonValue::asJsonObject)
                    .peek(o -> route.adaptFromJsonIndex(order.value++))
                    .map(route::adaptFromJson)
                    .collect(toList())
                : ROUTES_DEFAULT;

        Route exit = object.containsKey(EXIT_NAME)
                ? new Route(routes.size(), object.getString(EXIT_NAME))
                : null;

        return new Binding(vault, entry, type, kind, opts, routes, exit);
    }
}
