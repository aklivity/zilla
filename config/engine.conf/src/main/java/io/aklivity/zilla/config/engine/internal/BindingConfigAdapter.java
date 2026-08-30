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

import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingInfo;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.GenericBindingConfig;
import io.aklivity.zilla.config.engine.GenericBindingConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;

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

    private final String type;
    private final ConfigAdapter<OptionsConfig, JsonObject> options;
    private final KindConfigAdapter kind;
    private final RouteConfigAdapter route;
    private final SchemaConfigAdapter schema;
    private final TelemetryRefConfigAdapter telemetryRef;

    public BindingConfigAdapter(
        BindingInfo info)
    {
        this.type = info.type();
        this.options = info.options();
        this.kind = new KindConfigAdapter();
        this.route = new RouteConfigAdapter(info);
        this.schema = new SchemaConfigAdapter();
        this.telemetryRef = new TelemetryRefConfigAdapter();
    }

    public JsonObject adaptToJson(
        BindingConfig binding)
    {
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
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogedConfig catalog : binding.catalogs)
            {
                JsonArrayBuilder schemas = Json.createArrayBuilder();
                for (SchemaConfig schemaItem : catalog.schemas)
                {
                    schemas.add(schema.adaptToJson(schemaItem));
                }
                catalogs.add(catalog.name, schemas);
            }
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
                for (RouteConfig config : binding.routes)
                {
                    if (config != exitRoute)
                    {
                        routes.add(route.adaptToJson(config));
                    }
                }
                item.add(ROUTES_NAME, routes);
            }
        }

        if (binding.telemetryRef != null)
        {
            JsonObject telemetryRef0 = telemetryRef.adaptToJson(binding.telemetryRef);
            item.add(TELEMETRY_NAME, telemetryRef0);
        }

        return item.build();
    }

    public BindingConfig adaptFromJson(
        String namespace,
        String name,
        JsonObject value)
    {
        GenericBindingConfigBuilder<GenericBindingConfig> builder = GenericBindingConfig.builder()
            .namespace(namespace)
            .name(name)
            .type(type)
            .kind(kind.adaptFromJson(value.getJsonString(KIND_NAME)));

        if (value.containsKey(ENTRY_NAME))
        {
            builder.entry(value.getString(ENTRY_NAME));
        }

        if (value.containsKey(VAULT_NAME))
        {
            builder.vault(value.getString(VAULT_NAME));
        }

        if (value.containsKey(CATALOG_NAME))
        {
            JsonObject catalogsJson = value.getJsonObject(CATALOG_NAME);
            List<CatalogedConfig> catalogs = new ArrayList<>();
            for (String catalogName : catalogsJson.keySet())
            {
                JsonArray schemasJson = catalogsJson.getJsonArray(catalogName);
                List<SchemaConfig> schemas = new ArrayList<>();
                for (JsonValue schemaValue : schemasJson)
                {
                    schemas.add(schema.adaptFromJson(schemaValue.asJsonObject()));
                }
                catalogs.add(CatalogedConfig.builder().name(catalogName).schemas(schemas).build());
            }
            builder.catalogs(catalogs);
        }

        if (value.containsKey(OPTIONS_NAME))
        {
            builder.options(options.adaptFromJson(value.getJsonObject(OPTIONS_NAME)));
        }

        if (value.containsKey(ROUTES_NAME))
        {
            MutableInteger order = new MutableInteger();

            for (JsonValue object : value.getJsonArray(ROUTES_NAME))
            {
                builder.route(route.adaptFromJson(order.value++, object.asJsonObject()));
            }
        }

        if (value.containsKey(EXIT_NAME))
        {
            builder.exit(value.getString(EXIT_NAME));
        }

        if (value.containsKey(TELEMETRY_NAME))
        {
            builder.telemetry(telemetryRef.adaptFromJson(value.getJsonObject(TELEMETRY_NAME)));
        }

        return builder.build();
    }
}
