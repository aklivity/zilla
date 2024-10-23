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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpResponseConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiHeader;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiResponse;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiResponseByContentType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationsView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiPathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfigBuilder;
import io.aklivity.zilla.runtime.model.core.config.StringPattern;
import io.aklivity.zilla.runtime.model.core.internal.StringModel;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class OpenapiClientNamespaceGenerator extends OpenapiNamespaceGenerator
{
    @Override
    public NamespaceConfig generate(
        BindingConfig binding,
        OpenapiNamespaceConfig namespaceConfig)
    {
        final OpenapiOptionsConfig options = (OpenapiOptionsConfig) binding.options;
        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();
        List<OpenapiServerView> servers = namespaceConfig.servers;

        final int[] httpsPorts = resolvePortsForScheme("https", servers);
        final boolean secure = httpsPorts.length != 0;

        return NamespaceConfig.builder()
                .name(String.format(binding.qname, "$composite"))
                .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()))
                .binding()
                    .name("http_client0")
                    .type("http")
                    .kind(CLIENT)
                    .inject(b -> this.injectHttpClientOptions(b, namespaceConfig.openapis))
                    .inject(b -> this.injectMetrics(b, metricRefs, "http"))
                    .exit(secure ? "tls_client0" : "tcp_client0")
                    .build()
                .inject(b -> this.injectTlsClient(b, options.tls, secure, metricRefs))
                .binding()
                    .name("tcp_client0")
                    .type("tcp")
                    .kind(CLIENT)
                    .options(options.tcp)
                    .inject(b -> this.injectMetrics(b, metricRefs, "tcp"))
                    .build()
            .build();
    }

    private <C> BindingConfigBuilder<C> injectHttpClientOptions(
        BindingConfigBuilder<C> binding,
        List<Openapi> openApis)
    {
        for (Openapi openApi : openApis)
        {
            OpenapiOperationsView operations = OpenapiOperationsView.of(openApi.paths);
            if (operations.hasResponses())
            {
                binding.
                    options(HttpOptionsConfig::builder)
                        .inject(options -> injectHttpClientRequests(operations, options, openApi))
                        .build();
            }
        }
        return binding;
    }

    private <C> HttpOptionsConfigBuilder<C> injectHttpClientRequests(
        OpenapiOperationsView operations,
        HttpOptionsConfigBuilder<C> options,
        Openapi openApi)
    {
        for (String pathName : openApi.paths.keySet())
        {
            OpenapiPathView path = OpenapiPathView.of(openApi.paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                OpenapiOperationView operation = operations.operation(pathName, methodName);
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
        OpenapiOperationView operation,
        Openapi openApi)
    {
        if (operation != null && operation.responsesByStatus() != null)
        {
            for (Map.Entry<String, OpenapiResponseByContentType> responses0 : operation.responsesByStatus().entrySet())
            {
                String status = responses0.getKey();
                OpenapiResponseByContentType responses1 = responses0.getValue();
                if (!(OpenapiOperationView.DEFAULT.equals(status)) && responses1.content != null)
                {
                    for (Map.Entry<String, OpenapiResponse> response2 : responses1.content.entrySet())
                    {
                        OpenapiSchemaView schema = OpenapiSchemaView.of(openApi.components.schemas, response2.getValue().schema);
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
        OpenapiResponseByContentType responses,
        HttpResponseConfigBuilder<C> response)
    {
        if (responses.headers != null && !responses.headers.isEmpty())
        {
            for (Map.Entry<String, OpenapiHeader> header : responses.headers.entrySet())
            {
                String name = header.getKey();
                OpenapiSchema schema = header.getValue().schema;
                if (schema != null)
                {
                    String format = schema.format;
                    String type = schema.type;
                    ModelConfig model;
                    if (StringModel.NAME.equals(type))
                    {
                        StringModelConfigBuilder<StringModelConfig> builder = StringModelConfig.builder();
                        if (format != null)
                        {
                            builder.pattern(StringPattern.of(format));
                        }
                        model = builder.build();
                    }
                    else
                    {
                        model = MODELS.get(format != null ? String.format("%s:%s", type, format) : type);
                    }

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
        }
        return response;
    }

    private <C> NamespaceConfigBuilder<C> injectTlsClient(
        NamespaceConfigBuilder<C> namespace,
        TlsOptionsConfig tlsOptions,
        boolean secure,
        List<MetricRefConfig> metricRefs)
    {
        if (secure)
        {
            namespace
                .binding()
                    .name("tls_client0")
                    .type("tls")
                    .kind(CLIENT)
                    .options(tlsOptions)
                    .vault("client")
                    .inject(b -> injectMetrics(b, metricRefs, "tls"))
                    .exit("tcp_client0")
                    .build();
        }
        return namespace;
    }
}
