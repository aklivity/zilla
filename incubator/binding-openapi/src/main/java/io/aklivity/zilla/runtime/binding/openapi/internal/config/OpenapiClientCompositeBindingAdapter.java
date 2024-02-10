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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URI;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.config.HttpResponseConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Header;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Response;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.ResponseByContentType;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Schema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Server;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OperationsView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.PathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.SchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.ServerView;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.config.InlineSchemaConfigBuilder;
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
    private static final String INLINE_CATALOG_TYPE = "inline";
    private static final String VERSION_LATEST = "latest";

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
                    .options(TcpOptionsConfig::builder)
                        .host("") // env
                        .ports(new int[]{0}) // env
                        .build()
                    .build()
                .inject(n -> this.injectCatalog(n, openApi))
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
        for (Server item : openApi.servers)
        {
            ServerView server = ServerView.of(item);
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
        OperationsView operations = OperationsView.of(openApi.paths);
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
        OperationsView operations,
        HttpOptionsConfigBuilder<C> options,
        OpenApi openApi)
    {
        for (String pathName : openApi.paths.keySet())
        {
            PathView path = PathView.of(openApi.paths.get(pathName));
            for (String methodName : path.methods().keySet())
            {
                OperationView operation = operations.operation(pathName, methodName);
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
        OperationView operation,
        OpenApi openApi)
    {
        if (operation != null && operation.responsesByStatus() != null)
        {
            for (Map.Entry<String, ResponseByContentType> responses0 : operation.responsesByStatus().entrySet())
            {
                String status = responses0.getKey();
                ResponseByContentType responses1 = responses0.getValue();
                if (!(OperationView.DEFAULT.equals(status)) && responses1.content != null)
                {
                    for (Map.Entry<String, Response> response2 : responses1.content.entrySet())
                    {
                        SchemaView schema = SchemaView.of(openApi.components.schemas, response2.getValue().schema);
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
            for (Map.Entry<String, Header> header : responses.headers.entrySet())
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

    private <C> NamespaceConfigBuilder<C> injectCatalog(
        NamespaceConfigBuilder<C> namespace,
        OpenApi openApi)
    {
        if (openApi.components != null &&
            openApi.components.schemas != null &&
            !openApi.components.schemas.isEmpty())
        {
            namespace
                .catalog()
                    .name(INLINE_CATALOG_NAME)
                    .type(INLINE_CATALOG_TYPE)
                    .options(InlineOptionsConfig::builder)
                        .subjects()
                            .inject(s -> this.injectSubjects(s, openApi))
                            .build()
                        .build()
                    .build();
        }
        return namespace;
    }

    private <C> InlineSchemaConfigBuilder<C> injectSubjects(
        InlineSchemaConfigBuilder<C> subjects,
        OpenApi openApi)
    {
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            YAMLMapper yaml = YAMLMapper.builder()
                .disable(WRITE_DOC_START_MARKER)
                .enable(MINIMIZE_QUOTES)
                .build();
            for (Map.Entry<String, Schema> entry : openApi.components.schemas.entrySet())
            {
                SchemaView schema = SchemaView.of(openApi.components.schemas, entry.getValue());
                subjects
                    .subject(entry.getKey())
                        .version(VERSION_LATEST)
                        .schema(writeSchemaYaml(jsonb, yaml, schema))
                        .build();
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return subjects;
    }

    private static String writeSchemaYaml(
        Jsonb jsonb,
        YAMLMapper yaml,
        Object schema)
    {
        String result = null;
        try
        {
            String schemaJson = jsonb.toJson(schema);
            JsonNode json = new ObjectMapper().readTree(schemaJson);
            result = yaml.writeValueAsString(json);
        }
        catch (JsonProcessingException ex)
        {
            rethrowUnchecked(ex);
        }
        return result;
    }

}
