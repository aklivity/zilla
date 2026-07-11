/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.util.Collections.unmodifiableSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBinding;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiServerConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiServerConfigBuilder;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiAsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String OPENAPI_NAME = "openapi";
    private static final String ASYNCAPI_NAME = "asyncapi";
    private static final String SPECS_NAME = "specs";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";
    private static final String SECURITY_NAME = "security";
    private static final String OVERLAY_NAME = "overlay";
    private static final String SERVER_NAME = "server";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_URL_NAME = "url";
    private static final String SERVER_HOST_NAME = "host";
    private static final String SERVER_PATHNAME_NAME = "pathname";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return OpenapiAsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiAsyncapiOptionsConfig proxyOptions = (OpenapiAsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();
        JsonObjectBuilder spec = Json.createObjectBuilder();

        JsonObjectBuilder openapi = Json.createObjectBuilder();
        for (OpenapiSpecificationConfig openapiConfig : proxyOptions.specs.openapi)
        {
            final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
            final JsonArrayBuilder servers = Json.createArrayBuilder();
            final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

            if (openapiConfig.server != null)
            {
                catalogObject.add(SERVER_NAME, openapiConfig.server);
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

            if (openapiConfig.security != null && !openapiConfig.security.isEmpty())
            {
                final JsonObjectBuilder security = Json.createObjectBuilder();
                openapiConfig.security.forEach(security::add);
                catalogObject.add(SECURITY_NAME, security);
            }

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

            if (openapiConfig.servers != null && !openapiConfig.servers.isEmpty())
            {
                openapiConfig.servers.forEach(s ->
                {
                    JsonObjectBuilder server = Json.createObjectBuilder();
                    if (s.url != null && !s.url.isEmpty())
                    {
                        server.add(SERVER_URL_NAME, s.url);
                    }
                    servers.add(server);
                });
                catalogObject.add(SERVERS_NAME, servers);
            }

            openapi.add(openapiConfig.label, catalogObject);
        }
        spec.add(OPENAPI_NAME, openapi);

        JsonObjectBuilder asyncapi = Json.createObjectBuilder();
        for (AsyncapiSpecificationConfig asyncapiConfig : proxyOptions.specs.asyncapi)
        {
            final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
            final JsonArrayBuilder servers = Json.createArrayBuilder();
            final JsonObjectBuilder subjectObject = Json.createObjectBuilder();

            if (asyncapiConfig.server != null)
            {
                catalogObject.add(SERVER_NAME, asyncapiConfig.server);
            }

            for (AsyncapiCatalogConfig catalog : asyncapiConfig.catalogs)
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

            if (asyncapiConfig.security != null && !asyncapiConfig.security.isEmpty())
            {
                final JsonObjectBuilder security = Json.createObjectBuilder();
                asyncapiConfig.security.forEach(security::add);
                catalogObject.add(SECURITY_NAME, security);
            }

            if (asyncapiConfig.overlay != null)
            {
                final JsonObjectBuilder overlaySchema = Json.createObjectBuilder();
                overlaySchema.add(SUBJECT_NAME, asyncapiConfig.overlay.subject);
                if (asyncapiConfig.overlay.version != null)
                {
                    overlaySchema.add(VERSION_NAME, asyncapiConfig.overlay.version);
                }

                final JsonObjectBuilder overlaySubject = Json.createObjectBuilder();
                overlaySubject.add(asyncapiConfig.overlay.name, overlaySchema);
                catalogObject.add(OVERLAY_NAME, overlaySubject);
            }

            if (asyncapiConfig.servers != null && !asyncapiConfig.servers.isEmpty())
            {
                asyncapiConfig.servers.forEach(s ->
                {
                    JsonObjectBuilder server = Json.createObjectBuilder();
                    if (!s.host.isEmpty())
                    {
                        server.add(SERVER_HOST_NAME, s.host);
                    }
                    if (!s.url.isEmpty())
                    {
                        server.add(SERVER_URL_NAME, s.url);
                    }
                    if (!s.pathname.isEmpty())
                    {
                        server.add(SERVER_PATHNAME_NAME, s.pathname);
                    }
                    servers.add(server);
                });
                catalogObject.add(SERVERS_NAME, servers);
            }

            asyncapi.add(asyncapiConfig.label, catalogObject);
        }
        spec.add(ASYNCAPI_NAME, asyncapi);

        object.add(SPECS_NAME, spec);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonObject specs = object.getJsonObject(SPECS_NAME);

        JsonObject openapi = specs.getJsonObject(OPENAPI_NAME);
        Set<OpenapiSpecificationConfig> openapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : openapi.entrySet())
        {
            final String apiLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                final String server = specObject.containsKey(SERVER_NAME)
                    ? specObject.getString(SERVER_NAME)
                    : null;

                final JsonArray serversJson = specObject.getJsonArray(SERVERS_NAME);
                final List<OpenapiServerConfig> servers = new LinkedList<>();
                if (serversJson != null)
                {
                    serversJson.forEach(s ->
                    {
                        JsonObject serverObject = s.asJsonObject();
                        OpenapiServerConfigBuilder<OpenapiServerConfig> serverBuilder = OpenapiServerConfig.builder();
                        if (serverObject.containsKey(SERVER_URL_NAME))
                        {
                            serverBuilder.url(serverObject.getString(SERVER_URL_NAME));
                        }

                        servers.add(serverBuilder.build());
                    });
                }

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
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

                openapis.add(new OpenapiSpecificationConfig(apiLabel, server, servers, catalogs, security, overlay));
            }
        }

        JsonObject asyncapiObject = specs.getJsonObject(ASYNCAPI_NAME);
        Set<AsyncapiSpecificationConfig> asyncapis = new LinkedHashSet<>();
        for (Map.Entry<String, JsonValue> entry : asyncapiObject.entrySet())
        {
            final String apiLabel = entry.getKey();
            final JsonObject specObject = entry.getValue().asJsonObject();

            AsyncapiSpecificationConfigBuilder<AsyncapiSpecificationConfig> asyncapi = AsyncapiSpecificationConfig.builder()
                .label(apiLabel);

            if (specObject.containsKey(SERVER_NAME))
            {
                asyncapi.serverOverride(specObject.getString(SERVER_NAME));
            }

            if (specObject.containsKey(CATALOG_NAME))
            {
                final JsonObject catalogObject = specObject.getJsonObject(CATALOG_NAME);

                for (Map.Entry<String, JsonValue> catalogEntry : catalogObject.entrySet())
                {
                    String catalogName = catalogEntry.getKey();
                    JsonObject catalogValue = catalogEntry.getValue().asJsonObject();

                    AsyncapiCatalogConfigBuilder<?> catalog = asyncapi.catalog()
                        .name(catalogName);

                    if (catalogValue.containsKey(SUBJECT_NAME))
                    {
                        catalog.subject(catalogValue.getString(SUBJECT_NAME));
                    }

                    if (catalogValue.containsKey(VERSION_NAME))
                    {
                        catalog.version(catalogValue.getString(VERSION_NAME));
                    }

                    catalog.build();
                }

                if (specObject.containsKey(SECURITY_NAME))
                {
                    final JsonObject securityObject = specObject.getJsonObject(SECURITY_NAME);
                    for (String scheme : securityObject.keySet())
                    {
                        asyncapi.security(scheme, securityObject.getString(scheme));
                    }
                }

                if (specObject.containsKey(OVERLAY_NAME))
                {
                    final JsonObject overlayObject = specObject.getJsonObject(OVERLAY_NAME);
                    final Map.Entry<String, JsonValue> overlayEntry = overlayObject.entrySet().iterator().next();
                    final JsonObject overlaySchemaObject = overlayEntry.getValue().asJsonObject();

                    final AsyncapiCatalogConfigBuilder<?> overlayBuilder = asyncapi.overlay();
                    overlayBuilder.name(overlayEntry.getKey());

                    if (overlaySchemaObject.containsKey(SUBJECT_NAME))
                    {
                        overlayBuilder.subject(overlaySchemaObject.getString(SUBJECT_NAME));
                    }

                    if (overlaySchemaObject.containsKey(VERSION_NAME))
                    {
                        overlayBuilder.version(overlaySchemaObject.getString(VERSION_NAME));
                    }

                    overlayBuilder.build();
                }

                final JsonArray serversJson = specObject.getJsonArray(SERVERS_NAME);
                if (serversJson != null)
                {
                    for (JsonValue server : serversJson)
                    {
                        final AsyncapiServerConfigBuilder<?> serverBuilder = asyncapi.server();
                        final JsonObject serverObject = server.asJsonObject();

                        if (serverObject.containsKey(SERVER_HOST_NAME))
                        {
                            serverBuilder.host(serverObject.getString(SERVER_HOST_NAME));
                        }

                        if (serverObject.containsKey(SERVER_URL_NAME))
                        {
                            serverBuilder.url(serverObject.getString(SERVER_URL_NAME));
                        }

                        if (serverObject.containsKey(SERVER_PATHNAME_NAME))
                        {
                            serverBuilder.pathname(serverObject.getString(SERVER_PATHNAME_NAME));
                        }

                        serverBuilder.build();
                    }
                }

                asyncapis.add(asyncapi.build());
            }
        }

        OpenapiAsyncapiSpecConfig specConfig = new OpenapiAsyncapiSpecConfig(
            unmodifiableSet(openapis), unmodifiableSet(asyncapis));

        return new OpenapiAsyncapiOptionsConfig(specConfig);
    }
}
