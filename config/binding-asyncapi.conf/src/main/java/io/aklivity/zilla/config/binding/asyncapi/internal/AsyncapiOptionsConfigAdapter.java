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
package io.aklivity.zilla.config.binding.asyncapi.internal;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.asyncapi.AsyncapiCatalogConfig;
import io.aklivity.zilla.config.binding.asyncapi.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.config.binding.asyncapi.AsyncapiOptionsConfig;
import io.aklivity.zilla.config.binding.asyncapi.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.config.binding.asyncapi.AsyncapiSpecificationConfig;
import io.aklivity.zilla.config.binding.asyncapi.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class AsyncapiOptionsConfigAdapter extends ConfigAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String SECURITY_NAME = "security";
    private static final String STORE_NAME = "store";
    private static final String OVERLAY_NAME = "overlay";

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        AsyncapiOptionsConfig asyncapiOptions = (AsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (asyncapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (AsyncapiSpecificationConfig asyncapiConfig : asyncapiOptions.specs)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

                if (asyncapiConfig.servers != null && !asyncapiConfig.servers.isEmpty())
                {
                    final JsonArrayBuilder servers = Json.createArrayBuilder();
                    asyncapiConfig.servers.forEach(servers::add);
                    catalogObject.add(SERVERS_NAME, servers);
                }

                for (AsyncapiCatalogConfig catalog : asyncapiConfig.catalogs)
                {
                    JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                    schemaObject.add(SUBJECT_NAME, catalog.subject);

                    if (catalog.version != null)
                    {
                        schemaObject.add(VERSION_NAME, catalog.version);
                    }

                    if (catalog.overlay != null)
                    {
                        schemaObject.add(OVERLAY_NAME, overlayObject(catalog.overlay));
                    }

                    subjectObject.add(catalog.name, schemaObject);
                }
                catalogObject.add(CATALOG_NAME, subjectObject);

                if (asyncapiConfig.security != null && !asyncapiConfig.security.isEmpty())
                {
                    final JsonObjectBuilder security = Json.createObjectBuilder();
                    asyncapiConfig.security.forEach(security::add);
                    catalogObject.add(SECURITY_NAME, security);
                }

                if (asyncapiConfig.store != null)
                {
                    catalogObject.add(STORE_NAME, asyncapiConfig.store);
                }

                specs.add(asyncapiConfig.label, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        final AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> builder = AsyncapiOptionsConfig.builder();

        if (object.containsKey(SPECS_NAME))
        {
            final JsonObject specs = object.getJsonObject(SPECS_NAME);

            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
            {
                final AsyncapiSpecificationConfigBuilder<?> specBuilder = builder.spec();

                final String label = entry.getKey();
                specBuilder.label(label);

                final JsonObject spec = entry.getValue().asJsonObject();

                if (spec.containsKey(SERVERS_NAME))
                {
                    for (JsonValue serverValue : spec.getJsonArray(SERVERS_NAME))
                    {
                        specBuilder.serverOverride(((JsonString) serverValue).getString());
                    }
                }

                AsyncapiCatalogConfig deprecatedOverlay = null;
                if (spec.containsKey(OVERLAY_NAME))
                {
                    deprecatedOverlay = asOverlay(spec.getJsonObject(OVERLAY_NAME));
                }

                if (spec.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalogs = spec.getJsonObject(CATALOG_NAME);

                    for (Map.Entry<String, JsonValue> catalogEntry : catalogs.entrySet())
                    {
                        final String catalogName = catalogEntry.getKey();
                        final JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                        final AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> catalogBuilder =
                            AsyncapiCatalogConfig.builder();
                        catalogBuilder.name(catalogName);

                        if (catalogObject.containsKey(SUBJECT_NAME))
                        {
                            catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                        }

                        if (catalogObject.containsKey(VERSION_NAME))
                        {
                            catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                        }

                        final AsyncapiCatalogConfig catalogOverlay = catalogObject.containsKey(OVERLAY_NAME)
                            ? asOverlay(catalogObject.getJsonObject(OVERLAY_NAME))
                            : deprecatedOverlay;

                        if (catalogOverlay != null)
                        {
                            catalogBuilder.overlay(catalogOverlay);
                        }

                        specBuilder.catalog(catalogBuilder.build());
                    }
                }

                if (spec.containsKey(SECURITY_NAME))
                {
                    final JsonObject securityObject = spec.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        specBuilder.security(scheme, securityObject.getString(scheme));
                    }
                }

                if (spec.containsKey(STORE_NAME))
                {
                    specBuilder.store(spec.getString(STORE_NAME));
                }

                specBuilder.build();
            }
        }

        return builder.build();
    }

    private static AsyncapiCatalogConfig asOverlay(
        JsonObject overlayObject)
    {
        final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
        final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

        final AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfig> overlayBuilder = AsyncapiCatalogConfig.builder()
            .name(overlayEntry.getKey());

        if (overlaySchemaObject.containsKey(SUBJECT_NAME))
        {
            overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
        }

        if (overlaySchemaObject.containsKey(VERSION_NAME))
        {
            overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
        }

        return overlayBuilder.build();
    }

    private static JsonObjectBuilder overlayObject(
        AsyncapiCatalogConfig overlay)
    {
        final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
        overlaySchema.add(SUBJECT_NAME, overlay.subject);
        if (overlay.version != null)
        {
            overlaySchema.add(VERSION_NAME, overlay.version);
        }

        final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
        overlaySubject.add(overlay.name, overlaySchema);
        return overlaySubject;
    }
}
