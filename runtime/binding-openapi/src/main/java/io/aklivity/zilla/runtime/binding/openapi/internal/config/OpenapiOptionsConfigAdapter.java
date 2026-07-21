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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

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
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String SERVERS_NAME = "servers";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

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
                    JsonObjectBuilder schemaObject = Json.createObjectBuilder();
                    schemaObject.add(SUBJECT_NAME, catalog.subject);

                    if (catalog.version != null)
                    {
                        schemaObject.add(VERSION_NAME, catalog.version);
                    }

                    subjectObject.add(catalog.name, schemaObject);
                }
                catalogObject.add(CATALOG_NAME, subjectObject);

                if (openapiConfig.overlay != null)
                {
                    final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
                    overlaySchema.add(SUBJECT_NAME, openapiConfig.overlay.subject);
                    if (openapiConfig.overlay.version != null)
                    {
                        overlaySchema.add(VERSION_NAME, openapiConfig.overlay.version);
                    }

                    final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
                    overlaySubject.add(openapiConfig.overlay.name, overlaySchema);
                    catalogObject.add(OVERLAY_NAME, overlaySubject);
                }

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

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
                        JsonObject catalogObject = catalogEntry.getValue().asJsonObject();

                        catalogBuilder.name(catalogEntry.getKey());

                        if (catalogObject.containsKey(SUBJECT_NAME))
                        {
                            catalogBuilder.subject(catalogObject.getString(SUBJECT_NAME));
                        }

                        if (catalogObject.containsKey(VERSION_NAME))
                        {
                            catalogBuilder.version(catalogObject.getString(VERSION_NAME));
                        }
                        catalogs.add(catalogBuilder.build());
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

                OpenapiCatalogConfig overlay = null;
                if (specObject.containsKey(OVERLAY_NAME))
                {
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

                    OpenapiCatalogConfigBuilder<OpenapiCatalogConfig> overlayBuilder = OpenapiCatalogConfig.builder();
                    overlayBuilder.name(overlayEntry.getKey());

                    if (overlaySchemaObject.containsKey(SUBJECT_NAME))
                    {
                        overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
                    }

                    if (overlaySchemaObject.containsKey(VERSION_NAME))
                    {
                        overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
                    }
                    overlay = overlayBuilder.build();
                }

                openapiOptions.spec(new OpenapiSpecificationConfig(specLabel, servers, catalogs, security, overlay));
            }
        }

        return openapiOptions.build();
    }
}
