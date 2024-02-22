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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpResponseConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiHeader;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiResponse;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiServer;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.ResponseByContentType;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenApiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenApiOperationsView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenApiPathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenApiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenApiServerView;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.IntegerModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class OpenapiClientCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private static final String INLINE_CATALOG_NAME = "catalog0";

    private final Map<String, ModelConfig> models = Map.of(
        "string", StringModelConfig.builder().build(),
        "integer", IntegerModelConfig.builder().build()
    );

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    public OpenapiClientCompositeBindingAdapter()
    {
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        OpenapiOptionsConfig options = (OpenapiOptionsConfig) binding.options;
        OpenapiConfig openapiConfig = options.openapis.get(0);

        final OpenApi openApi = openapiConfig.openapi;
        final int[] httpsPorts = resolvePortsForScheme(openApi, "https");
        final boolean secure = httpsPorts != null;

        return BindingConfig.builder(binding)
            .composite()
                .name(String.format(binding.qname, "$composite"))
                .binding()
                    .name("http_client0")
                    .type("http")
                    .kind(CLIENT)
                    .inject(b -> this.injectHttpClientOptions(b, openApi))
                    .exit(secure ? "tls_client0" : "tcp_client0")
                    .build()
                .inject(b -> this.injectTlsClient(b, options.tls, secure))
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .options(options.tcp)
                    .build()
                .build()
            .build();
    }

    private int[] resolvePortsForScheme(
        OpenApi openApi,
        String scheme)
    {
        requireNonNull(scheme);
        int[] ports = null;
        URI url = findFirstServerUrlWithScheme(openApi, scheme);
        if (url != null)
        {
            ports = new int[] {url.getPort()};
        }
        return ports;
    }

    private URI findFirstServerUrlWithScheme(
        OpenApi openApi,
        String scheme)
    {
        requireNonNull(scheme);
        URI result = null;
        for (OpenApiServer item : openApi.servers)
        {
            OpenApiServerView server = OpenApiServerView.of(item);
            if (scheme.equals(server.url().getScheme()))
            {
                result = server.url();
                break;
            }
        }
        return result;
    }

    private <C> BindingConfigBuilder<C> injectHttpClientOptions(
        BindingConfigBuilder<C> binding,
        OpenApi openApi)
    {
        OpenApiOperationsView operations = OpenApiOperationsView.of(openApi.paths);
        if (operations.hasResponses())
        {
            binding.
                options(HttpOptionsConfig::builder)
                    .inject(options -> injectHttpClientRequests(operations, options, openApi))
                    .build();
        }
        return binding;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpClientRequests(
        OpenApiOperationsView operations,
        HttpOptionsConfigBuilder<C> options,
        OpenApi openApi)
    {
        for (String pathName : openApi.paths.keySet())
        {
            OpenApiPathView path = OpenApiPathView.of(openApi.paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                OpenApiOperationView operation = operations.operation(pathName, methodName);
                if (operation.hasResponses())
                {
                    options
                        .request()
                            .path(pathName)
                            .method(HttpRequestConfig.Method.valueOf(methodName))
                            .inject(request -> injectResponses(request, operation, openApi))
                            .build()
                        .build();
                }
            }
        }
        return options;
    }

    private <C> HttpRequestConfigBuilder<C> injectResponses(
        HttpRequestConfigBuilder<C> request,
        OpenApiOperationView operation,
        OpenApi openApi)
    {
        if (operation != null && operation.responsesByStatus() != null)
        {
            for (Map.Entry<String, ResponseByContentType> responses0 : operation.responsesByStatus().entrySet())
            {
                String status = responses0.getKey();
                ResponseByContentType responses1 = responses0.getValue();
                if (!(OpenApiOperationView.DEFAULT.equals(status)) && responses1.content != null)
                {
                    for (Map.Entry<String, OpenApiResponse> response2 : responses1.content.entrySet())
                    {
                        OpenApiSchemaView schema = OpenApiSchemaView.of(openApi.components.schemas, response2.getValue().schema);
                        request
                            .response()
                                .status(Integer.parseInt(status))
                                .contentType(response2.getKey())
                                .inject(response -> injectResponseHeaders(responses1, response))
                                .content(JsonModelConfig::builder)
                                    .catalog()
                                    .name(INLINE_CATALOG_NAME)
                                    .schema()
                                        .subject(schema.refKey())
                                        .build()
                                    .build()
                                .build()
                            .build();
                    }
                }
            }
        }
        return request;
    }

    private <C> HttpResponseConfigBuilder<C> injectResponseHeaders(
        ResponseByContentType responses,
        HttpResponseConfigBuilder<C> response)
    {
        if (responses.headers != null && !responses.headers.isEmpty())
        {
            for (Map.Entry<String, OpenApiHeader> header : responses.headers.entrySet())
            {
                String name = header.getKey();
                ModelConfig model = models.get(header.getValue().schema.type);
                if (model != null)
                {
                    response
                        .header()
                            .name(name)
                            .model(model)
                            .build();
                }
            }
        }
        return response;
    }

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace,
        TlsOptionsConfig tlsConfig,
        boolean secure)
    {
        if (secure)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .options(tlsConfig)
                    .vault("client")
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }

}
