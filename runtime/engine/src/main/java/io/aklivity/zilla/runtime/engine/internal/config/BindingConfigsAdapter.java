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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;

public class BindingConfigsAdapter implements JsonbAdapter<BindingConfig[], JsonObject>
{
    private static final String VAULT_NAME = "vault";
    private static final String CATALOG_NAME = "catalog";
    private static final String EXIT_NAME = "exit";
    private static final String TYPE_NAME = "type";
    private static final String KIND_NAME = "kind";
    private static final String ENTRY_NAME = "entry";
    private static final String OPTIONS_NAME = "options";
    private static final String ROUTES_NAME = "routes";
    private static final String TELEMETRY_NAME = "telemetry";

    private static final List<RouteConfig> ROUTES_DEFAULT = emptyList();

    private final KindAdapter kind;
    private final RouteAdapter route;
    private final OptionsAdapter options;
    private final TelemetryRefAdapter telemetryRef;

    public BindingConfigsAdapter(
        ConfigAdapterContext context)
    {
        this.kind = new KindAdapter(context);
        this.route = new RouteAdapter(context);
        this.options = new OptionsAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        this.telemetryRef = new TelemetryRefAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        BindingConfig[] bindings)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        for (BindingConfig binding : bindings)
        {
            route.adaptType(binding.type);
            options.adaptType(binding.type);

            JsonObjectBuilder item = Json.createObjectBuilder();

            if (binding.vault != null)
            {
                item.add(VAULT_NAME, binding.vault);
            }

            item.add(TYPE_NAME, binding.type);

            item.add(KIND_NAME, kind.adaptToJson(binding.kind));

            if (binding.entry != null)
            {
                item.add(ENTRY_NAME, binding.entry);
            }

            if (binding.options != null)
            {
                item.add(OPTIONS_NAME, options.adaptToJson(binding.options));
            }

            if (!ROUTES_DEFAULT.equals(binding.routes))
            {
                JsonArrayBuilder routes = Json.createArrayBuilder();
                binding.routes.forEach(r -> routes.add(route.adaptToJson(r)));
                item.add(ROUTES_NAME, routes);
            }

            if (binding.telemetryRef != null)
            {
                JsonObject telemetryRef0 = telemetryRef.adaptToJson(binding.telemetryRef);
                item.add(TELEMETRY_NAME, telemetryRef0);
            }

            object.add(binding.name, item);
        }

        return object.build();
    }

    @Override
    public BindingConfig[] adaptFromJson(
        JsonObject object)
    {
        List<BindingConfig> bindings = new LinkedList<>();

        for (String name : object.keySet())
        {
            JsonObject item = object.getJsonObject(name);
            String type = item.getString(TYPE_NAME);

            route.adaptType(type);
            options.adaptType(type);

            String vault = item.containsKey(VAULT_NAME)
                    ? item.getString(VAULT_NAME)
                    : null;
            KindConfig kind = this.kind.adaptFromJson(item.getJsonString(KIND_NAME));
            OptionsConfig opts = item.containsKey(OPTIONS_NAME) ?
                    options.adaptFromJson(item.getJsonObject(OPTIONS_NAME)) :
                    null;
            MutableInteger order = new MutableInteger();
            List<RouteConfig> routes = item.containsKey(ROUTES_NAME)
                    ? item.getJsonArray(ROUTES_NAME)
                        .stream()
                        .map(JsonValue::asJsonObject)
                        .peek(o -> route.adaptFromJsonIndex(order.value++))
                        .map(route::adaptFromJson)
                        .collect(toList())
                    : ROUTES_DEFAULT;

            RouteConfig exit = item.containsKey(EXIT_NAME)
                    ? new RouteConfig(routes.size(), item.getString(EXIT_NAME))
                    : null;

            if (exit != null)
            {
                List<RouteConfig> routesWithExit = new ArrayList<>();
                routesWithExit.addAll(routes);
                routesWithExit.add(exit);
                routes = routesWithExit;
            }

            TelemetryRefConfig telemetryRef = item.containsKey(TELEMETRY_NAME)
                    ? this.telemetryRef.adaptFromJson(item.getJsonObject(TELEMETRY_NAME))
                    : null;

            String entry = item.containsKey(ENTRY_NAME)
                ? item.getString(ENTRY_NAME)
                : null;

            bindings.add(new BindingConfig(vault, name, type, kind, entry, opts, routes, telemetryRef));
        }

        return bindings.toArray(BindingConfig[]::new);
    }
}
