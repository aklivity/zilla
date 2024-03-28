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

import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
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

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
    private OptionsConfigAdapter httpOptions;
    private Function<String, String> readURL;

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
