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

import static io.aklivity.zilla.config.engine.BindingConfigBuilder.ROUTES_DEFAULT;

import java.util.Optional;
import java.util.regex.Matcher;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfigBuilder;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.OptionsConfigAdapter;
import io.aklivity.zilla.config.engine.RouteConfig;

public class BindingConfigAdapter
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

    private final KindConfigAdapter kind;
    private final RouteConfigAdapter route;
    private final OptionsConfigAdapter options;
    private final CatalogedConfigAdapter cataloged;
    private final TelemetryRefConfigAdapter telemetryRef;

    private String namespace;

    public BindingConfigAdapter(
        EngineInfo info)
    {
        this.kind = new KindConfigAdapter();
        this.route = new RouteConfigAdapter(info);
        this.options = new OptionsConfigAdapter(info::binding);
        this.cataloged = new CatalogedConfigAdapter();
        this.telemetryRef = new TelemetryRefConfigAdapter();
    }

    public void adaptNamespace(
        String namespace)
    {
        this.namespace = namespace;
    }

    public JsonObject adaptToJson(
        BindingConfig binding) throws Exception
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

        return item.build();
    }

    public BindingConfig adaptFromJson(
        String name,
        JsonObject item) throws Exception
    {
        Matcher matcher = NamespaceConfig.PATTERN_NAME.matcher(name);
        if (!matcher.matches())
        {
            throw new IllegalStateException(String.format("%s does not match pattern", name));
        }

        String type = item.getString(TYPE_NAME);
        route.adaptType(type);
        options.adaptType(type);

        GenericBindingConfigBuilder<GenericBindingConfig> binding = GenericBindingConfig.builder()
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

        return binding.build();
    }
}
