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
package io.aklivity.zilla.config.binding.openapi.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.openapi.OpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.openapi.OpenapiOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.SchemaConfigAdapter;
import io.aklivity.zilla.config.engine.SchemaConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;

public final class OpenapiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String CATALOG_NAME = "catalog";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiOptionsConfig openapiOptions = (OpenapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (openapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (OpenapiSpecificationConfig openapiConfig : openapiOptions.specs)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

                if (openapiConfig.servers != null && !openapiConfig.servers.isEmpty())
                {
                    final JsonArrayBuilder servers = Json.createArrayBuilder();
                    openapiConfig.servers.forEach(servers::add);
                    catalogObject.add(SERVERS_NAME, servers);
                }

                for (OpenapiCatalogConfig catalog : openapiConfig.catalogs)
                {
                    subjectObject.add(catalog.name, schema.adaptToJson(asSchemaConfig(catalog)));
                }
                catalogObject.add(CATALOG_NAME, subjectObject);

                if (openapiConfig.security != null && !openapiConfig.security.isEmpty())
                {
                    final JsonObjectBuilder security = Json.createObjectBuilder();
                    openapiConfig.security.forEach(security::add);
                    catalogObject.add(SECURITY_NAME, security);
                }

                specs.add(openapiConfig.label, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OpenapiOptionsConfigBuilder<OpenapiOptionsConfig> openapiOptions = OpenapiOptionsConfig.builder();

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject specs = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
            {
                final String specLabel = entry.getKey();
                final JsonObject specObject = entry.getValue().asJsonObject();

                List<String> servers = null;
                if (specObject.containsKey(SERVERS_NAME))
                {
                    servers = new ArrayList<>();
                    for (JsonValue serverValue : specObject.getJsonArray(SERVERS_NAME))
                    {
                        servers.add(((JsonString) serverValue).getString());
                    }
                }

                OpenapiCatalogConfig deprecatedOverlay = null;
                if (specObject.containsKey(OVERLAY_NAME))
                {
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    deprecatedOverlay = asCatalogConfig(overlayEntry.getKey(), overlayEntry.getValue().asJsonObject());
                }

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        final SchemaConfig parsed = schema.adaptFromJson(catalogEntry.getValue().asJsonObject());
                        final OpenapiCatalogConfig overlay = parsed.overlay != null
                            ? asCatalogConfig(parsed.overlay.name, parsed.overlay.schema)
                            : deprecatedOverlay;

                        catalogs.add(OpenapiCatalogConfig.builder()
                            .name(catalogEntry.getKey())
                            .subject(parsed.subject)
                            .version(parsed.version)
                            .overlay(overlay)
                            .build());
                    }
                }

                Map<String, String> security = null;
                if (specObject.containsKey(SECURITY_NAME))
                {
                    security = new LinkedHashMap<>();
                    final JsonObject securityObject = specObject.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        security.put(scheme, securityObject.getString(scheme));
                    }
                }

                openapiOptions.spec(new OpenapiSpecificationConfig(specLabel, servers, catalogs, security));
            }
        }

        return openapiOptions.build();
    }

    private OpenapiCatalogConfig asCatalogConfig(
        String name,
        JsonObject catalogObject)
    {
        SchemaConfig parsed = schema.adaptFromJson(catalogObject);

        return asCatalogConfig(name, parsed);
    }

    private OpenapiCatalogConfig asCatalogConfig(
        String name,
        SchemaConfig parsed)
    {
        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> builder = OpenapiCatalogConfig.builder()
            .name(name)
            .subject(parsed.subject)
            .version(parsed.version);

        if (parsed.overlay != null)
        {
            builder.overlay(asCatalogConfig(parsed.overlay.name, parsed.overlay.schema));
        }

        return builder.build();
    }

    private SchemaConfig asSchemaConfig(
        OpenapiCatalogConfig catalog)
    {
        SchemaConfigBuilder<SchemaConfig> builder = SchemaConfig.builder()
            .subject(catalog.subject)
            .version(catalog.version);

        if (catalog.overlay != null)
        {
            builder.overlay(OverlayConfig.builder()
                .name(catalog.overlay.name)
                .schema(asSchemaConfig(catalog.overlay))
                .build());
        }

        return builder.build();
    }
}
