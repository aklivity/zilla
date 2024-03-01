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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.Function;
import java.util.zip.CRC32C;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
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
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String SPECS_NAME = "specs";

    private final OpenapiParser parser;
    private final CRC32C crc;

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
    private OptionsConfigAdapter httpOptions;
    private Function<String, String> readURL;

    public OpenapiOptionsConfigAdapter()
    {
        this.parser = new OpenapiParser();
        this.crc = new CRC32C();
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
            JsonObjectBuilder openapi = Json.createObjectBuilder();
            openapiOptions.openapis.forEach(o -> openapi.add(o.apiLabel, o.location));
            object.add(SPECS_NAME, openapi);
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
            openapi.forEach((n, v) ->
            {
                final String location = JsonString.class.cast(v).getString();
                final String specText = readURL.apply(location);
                final String apiLabel = n;
                crc.reset();
                crc.update(specText.getBytes(UTF_8));
                final long apiId = crc.getValue();
                openapiOptions.openapi(new OpenapiConfig(apiLabel, apiId, location, parser.parse(specText)));
            });
        }

        return openapiOptions.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
        this.tcpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tcpOptions.adaptType("tcp");
        this.tlsOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tlsOptions.adaptType("tls");
        this.httpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.httpOptions.adaptType("http");
    }
}
