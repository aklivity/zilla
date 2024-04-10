/*
 * Copyright 2021-2023 Aklivity Inc
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenpaiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenpaiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String SERVERS_NAME = "servers";
    private static final String SERVER_NAME_NAME = "name";
    private static final String SERVER_HOST_NAME = "host";
    private static final String SERVER_URL_NAME = "url";
    private static final String SERVER_PATHNAME_NAME = "pathname";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
    private OptionsConfigAdapter httpOptions;

    public OpenapiOptionsConfigAdapter()
    {
    }

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

        if (openapiOptions.servers != null)
        {
            JsonArrayBuilder servers = Json.createArrayBuilder();
            openapiOptions.servers.forEach(servers::add);
            object.add(SERVERS_NAME, servers);
        }

        if (openapiOptions.tcp != null)
        {
            final TcpOptionsConfig tcp = ((OpenapiOptionsConfig) options).tcp;
            object.add(TCP_NAME, tcpOptions.adaptToJson(tcp));
        }

        if (openapiOptions.tls != null)
        {
            final TlsOptionsConfig tls = ((OpenapiOptionsConfig) options).tls;
            object.add(TLS_NAME, tlsOptions.adaptToJson(tls));
        }

        HttpOptionsConfig http = openapiOptions.http;
        if (http != null)
        {
            object.add(HTTP_NAME, httpOptions.adaptToJson(http));
        }

        if (openapiOptions.openapis != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (OpenapiConfig openapiConfig : openapiOptions.openapis)
            {
                final JsonObjectBuilder catalogObject = Json.createObjectBuilder();
                final JsonArrayBuilder servers = Json.createArrayBuilder();
                final JsonObjectBuilder subjectObject = Json.createObjectBuilder();
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

                if (openapiConfig.servers != null)
                {
                    openapiConfig.servers.forEach(s ->
                    {
                        JsonObjectBuilder server = Json.createObjectBuilder();
                        if (s.name != null)
                        {
                            server.add(SERVER_NAME_NAME, s.name);
                        }
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
                }
                catalogObject.add(SERVERS_NAME, servers);

                specs.add(openapiConfig.apiLabel, catalogObject);
            }
            object.add(SPECS_NAME, specs);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OpenpaiOptionsConfigBuilder<OpenapiOptionsConfig> openapiOptions = OpenapiOptionsConfig.builder();

        if (object.containsKey(TCP_NAME))
        {
            final JsonObject tcp = object.getJsonObject(TCP_NAME);
            final TcpOptionsConfig tcpOptions = (TcpOptionsConfig) this.tcpOptions.adaptFromJson(tcp);
            openapiOptions.tcp(tcpOptions);
        }

        if (object.containsKey(TLS_NAME))
        {
            final JsonObject tls = object.getJsonObject(TLS_NAME);
            final TlsOptionsConfig tlsOptions = (TlsOptionsConfig) this.tlsOptions.adaptFromJson(tls);
            openapiOptions.tls(tlsOptions);
        }

        if (object.containsKey(HTTP_NAME))
        {
            JsonObject http = object.getJsonObject(HTTP_NAME);

            final HttpOptionsConfig httpOptions = (HttpOptionsConfig) this.httpOptions.adaptFromJson(http);
            openapiOptions.http(httpOptions);
        }

        if (object.containsKey(SPECS_NAME))
        {
            JsonObject openapi = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> entry : openapi.entrySet())
            {
                final String apiLabel = entry.getKey();
                final JsonObject specObject = entry.getValue().asJsonObject();
                final JsonArray serversJson = specObject.getJsonArray(SERVERS_NAME);

                final List<OpenapiServerConfig> servers = new LinkedList<>();
                if (serversJson != null)
                {
                    serversJson.forEach(s ->
                    {
                        JsonObject serverObject = s.asJsonObject();
                        OpenapiServerConfigBuilder<OpenapiServerConfig> serverBuilder = OpenapiServerConfig.builder();
                        if (serverObject.containsKey(SERVER_NAME_NAME))
                        {
                            serverBuilder.name(serverObject.getString(SERVER_NAME_NAME));
                        }

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
                        servers.add(serverBuilder.build());
                    });
                }

                List<OpenapiCatalogConfig> catalogs = new ArrayList<>();
                if (specObject.containsKey(CATALOG_NAME))
                {
                    final JsonObject catalog = specObject.getJsonObject(CATALOG_NAME);

                    for (Map.Entry<String, JsonValue> catalogEntry : catalog.entrySet())
                    {
                        OpenpaiCatalogConfigBuilder<OpenapiCatalogConfig> catalogBuilder = OpenapiCatalogConfig.builder();
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
                openapiOptions.openapi(new OpenapiConfig(apiLabel, servers, catalogs));
            }
        }

        return openapiOptions.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.tcpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tcpOptions.adaptType("tcp");
        this.tlsOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tlsOptions.adaptType("tls");
        this.httpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.httpOptions.adaptType("http");
    }
}
