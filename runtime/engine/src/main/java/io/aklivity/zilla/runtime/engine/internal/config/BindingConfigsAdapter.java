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
package io.aklivity.zilla.runtime.engine.internal.config;

import static io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder.ROUTES_DEFAULT;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

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

    private final KindAdapter kind;
    private final RouteAdapter route;
    private final OptionsConfigAdapter options;
    private final CatalogedAdapter cataloged;
    private final TelemetryRefAdapter telemetryRef;

    private String namespace;

    public BindingConfigsAdapter(
        ConfigAdapterContext context)
    {
        this.kind = new KindAdapter(context);
        this.route = new RouteAdapter(context);
        this.options = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
        this.cataloged = new CatalogedAdapter();
        this.telemetryRef = new TelemetryRefAdapter();
    }

    public BindingConfigsAdapter adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
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

            item.add(TYPE_NAME, binding.type);

            item.add(KIND_NAME, kind.adaptToJson(binding.kind));

            if (binding.entry != null)
            {
                item.add(ENTRY_NAME, binding.entry);
            }

            if (binding.vault != null)
            {
                item.add(VAULT_NAME, binding.vault);
            }

            if (binding.options != null)
            {
                item.add(OPTIONS_NAME, options.adaptToJson(binding.options));
            }

            if (binding.catalogs != null && !binding.catalogs.isEmpty())
            {
                JsonArrayBuilder catalogs = Json.createArrayBuilder();
                catalogs.add(cataloged.adaptToJson(binding.catalogs));
                item.add(CATALOG_NAME, catalogs);
            }

            if (!ROUTES_DEFAULT.equals(binding.routes))
            {
                final RouteConfig lastRoute = binding.routes.get(binding.routes.size() - 1);
                final RouteConfig exitRoute =
                    lastRoute.exit != null &&
                    lastRoute.guarded.isEmpty() &&
                    lastRoute.when.isEmpty() &&
                    lastRoute.with == null
                        ? lastRoute
                        : null;

                if (exitRoute != null)
                {
                    item.add(EXIT_NAME, lastRoute.exit);
                }

                if (exitRoute == null || binding.routes.size() > 1)
                {
                    JsonArrayBuilder routes = Json.createArrayBuilder();
                    binding.routes.stream()
                        .filter(r -> r != exitRoute)
                        .forEach(r -> routes.add(route.adaptToJson(r)));
                    item.add(ROUTES_NAME, routes);
                }
            }

            if (binding.telemetryRef != null)
            {
                JsonObject telemetryRef0 = telemetryRef.adaptToJson(binding.telemetryRef);
                item.add(TELEMETRY_NAME, telemetryRef0);
            }

            assert namespace.equals(binding.namespace);
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
            Matcher matcher = NamespaceAdapter.PATTERN_NAME.matcher(name);
            if (!matcher.matches())
            {
                throw new IllegalStateException(String.format("%s does not match pattern", name));
            }

            JsonObject item = object.getJsonObject(name);

            String type = item.getString(TYPE_NAME);
            route.adaptType(type);
            options.adaptType(type);

            BindingConfigBuilder<BindingConfig> binding = BindingConfig.builder()
                .namespace(Optional.ofNullable(matcher.group("namespace")).orElse(namespace))
                .name(matcher.group("name"))
                .type(type)
                .kind(kind.adaptFromJson(item.getJsonString(KIND_NAME)));

            if (item.containsKey(ENTRY_NAME))
            {
                binding.entry(item.getString(ENTRY_NAME));
            }

            if (item.containsKey(VAULT_NAME))
            {
                binding.vault(item.getString(VAULT_NAME));
            }

            if (item.containsKey(CATALOG_NAME))
            {
                binding.catalogs(cataloged.adaptFromJson(item.getJsonObject(CATALOG_NAME)));
            }

            if (item.containsKey(OPTIONS_NAME))
            {
                binding.options(options.adaptFromJson(item.getJsonObject(OPTIONS_NAME)));
            }

            if (item.containsKey(ROUTES_NAME))
            {
                MutableInteger order = new MutableInteger();

                item.getJsonArray(ROUTES_NAME)
                    .stream()
                    .map(JsonValue::asJsonObject)
                    .peek(o -> route.adaptFromJsonIndex(order.value++))
                    .map(route::adaptFromJson)
                    .forEach(binding::route);
            }

            if (item.containsKey(EXIT_NAME))
            {
                binding.exit(item.getString(EXIT_NAME));
            }

            if (item.containsKey(TELEMETRY_NAME))
            {
                binding.telemetry(telemetryRef.adaptFromJson(item.getJsonObject(TELEMETRY_NAME)));
            }

            bindings.add(binding.build());
        }

        return bindings.toArray(BindingConfig[]::new);
    }
}
