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
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;
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
    private static final String SERVER_URL_NAME = "url";
    private static final String CATALOG_NAME = "catalog";
    private static final String SUBJECT_NAME = "subject";
    private static final String VERSION_NAME = "version";

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
    private OptionsConfigAdapter httpOptions;

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

        if (openapiOptions.specs != null)
        {
            final JsonObjectBuilder specs = Json.createObjectBuilder();
            for (OpenapiSpecificationConfig openapiConfig : openapiOptions.specs)
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

                if (openapiConfig.servers != null && !openapiConfig.servers.isEmpty())
                {
                    openapiConfig.servers.forEach(s ->
                    {
                        JsonObjectBuilder server = Json.createObjectBuilder();
                        if (!s.url.isEmpty())
                        {
                            server.add(SERVER_URL_NAME, s.url);
                        }
                        servers.add(server);
                    });
                    catalogObject.add(SERVERS_NAME, servers);
                }

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
        OpenapiOptionsConfigBuilder<OpenapiOptionsConfig> openapiOptions = OpenapiOptionsConfig.builder();

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
            JsonObject specs = object.getJsonObject(SPECS_NAME);
            for (Map.Entry<String, JsonValue> entry : specs.entrySet())
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
                        if (serverObject.containsKey(SERVER_URL_NAME))
                        {
                            serverBuilder.url(serverObject.getString(SERVER_URL_NAME));
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
                openapiOptions.spec(new OpenapiSpecificationConfig(apiLabel, servers, catalogs));
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
