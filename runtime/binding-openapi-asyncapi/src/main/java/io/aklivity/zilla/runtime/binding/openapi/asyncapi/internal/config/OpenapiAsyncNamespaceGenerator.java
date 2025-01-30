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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiReplyView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiHeaderView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiMediaTypeView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiPathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiResponseView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public final class OpenapiAsyncNamespaceGenerator
{
    private static final String CORRELATION_ID = "\\{correlationId\\}";
    private static final String PARAMETERS = "\\{(?!correlationId)(\\w+)\\}";
    private static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern CORRELATION_PATTERN = Pattern.compile(CORRELATION_ID);

    private final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    private final Matcher parameters = PARAMETER_PATTERN.matcher("");
    private final Matcher correlation = CORRELATION_PATTERN.matcher("");

    public NamespaceConfig generate(
        BindingConfig binding,
        Map<String, OpenapiView> openapis,
        Map<String, AsyncapiView> asyncapis,
        ToLongFunction<String> resolveApiId)
    {
        final List<MetricRefConfig> metricRefs = binding.telemetryRef != null ?
            binding.telemetryRef.metricRefs : emptyList();

        List<OpenapiAsyncapiRouteConfig> routes = binding.routes.stream()
            .map(r -> new OpenapiAsyncapiRouteConfig(r, resolveApiId))
            .collect(toList());

        return NamespaceConfig.builder()
                    .name(String.format("%s/http_kafka", binding.qname))
                    .inject(n -> this.injectNamespaceMetric(n, !metricRefs.isEmpty()))
                    .binding()
                        .name("http_kafka0")
                        .type("http-kafka")
                        .kind(PROXY)
                        .inject(b -> this.injectMetrics(b, metricRefs, "http-kafka"))
                        .inject(b -> this.injectHttpKafkaRoutes(b, binding.qname, openapis, asyncapis, routes))
                        .build()
                .build();
    }

    private <C> BindingConfigBuilder<C> injectHttpKafkaRoutes(
        BindingConfigBuilder<C> binding,
        String qname,
        Map<String, OpenapiView> openapis,
        Map<String, AsyncapiView> asyncapis,
        List<OpenapiAsyncapiRouteConfig> routes)
    {
        for (OpenapiAsyncapiRouteConfig route : routes)
        {
            for (OpenapiAsyncapiConditionConfig condition : route.when)
            {
                Optional<OpenapiView> openapiConfig = openapis.entrySet().stream()
                    .filter(e -> e.getKey().equals(condition.apiId))
                    .map(Map.Entry::getValue)
                    .findFirst();
                Optional<AsyncapiView> asyncapiConfig = asyncapis.entrySet().stream()
                    .filter(e -> e.getKey().equals(route.with.apiId))
                    .map(Map.Entry::getValue)
                    .findFirst();

                if (openapiConfig.isPresent() && asyncapiConfig.isPresent())
                {
                    final OpenapiView openapi = openapiConfig.get();
                    final AsyncapiView asyncapi = asyncapiConfig.get();

                    computeRoutes(binding, qname, condition, openapi, asyncapi);
                }

            }
        }

        return binding;
    }

    private <C> void computeRoutes(
        BindingConfigBuilder<C> binding,
        String qname,
        OpenapiAsyncapiConditionConfig condition,
        OpenapiView openapi,
        AsyncapiView asyncapi)
    {
        for (String item : openapi.paths.keySet())
        {
            OpenapiPathView path = openapi.paths.get(item);
            for (String method : path.methods.keySet())
            {
                final String operationId = condition.operationId != null
                    ? condition.operationId
                    : path.methods.get(method).id;

                final OpenapiOperationView openapiOperation = path.methods.get(method);
                final Optional<AsyncapiOperationView> asyncapiOperation = findAsyncOperation(
                    item, openapi, asyncapi, openapiOperation, operationId);

                asyncapiOperation.ifPresent(operation ->
                {
                    final List<String> paramNames = findParams(item);

                    binding
                        .route()
                        .exit(qname)
                        .when(HttpKafkaConditionConfig::builder)
                        .method(method)
                        .path(item)
                        .build()
                        .inject(r -> injectHttpKafkaRouteWith(r, openapi, openapiOperation, operation, paramNames))
                        .build();
                });
            }
        }
    }

    private Optional<AsyncapiOperationView> findAsyncOperation(
        String path,
        OpenapiView openapi,
        AsyncapiView asyncapi,
        OpenapiOperationView openapiOperation,
        String operationId)
    {
        Optional<AsyncapiOperationView> operation = findAsyncOperationByOperationId(asyncapi.operations, operationId);

        if (operation.isEmpty() && isOpenapiOperationAsync(openapiOperation))
        {
            Optional<String> correlatedOperationId = findOpenapiOperationIdByFormat(path, openapi);
            if (correlatedOperationId.isPresent())
            {
                operation = findAsyncOperationByOperationId(asyncapi.operations, correlatedOperationId.get());
            }
        }
        return operation;
    }

    private Optional<AsyncapiOperationView> findAsyncOperationByOperationId(
        Map<String, AsyncapiOperationView> operations,
        String operationId)
    {
        return operations.entrySet().stream()
            .filter(f -> f.getKey().equals(operationId))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    private Optional<String> findOpenapiOperationIdByFormat(
        String format,
        OpenapiView openapi)
    {
        String operationId = null;
        correlated:
        for (String item : openapi.paths.keySet())
        {
            if (!item.equals(format))
            {
                OpenapiPathView path = openapi.paths.get(item);
                for (String method : path.methods.keySet())
                {
                    final OpenapiOperationView openapiOperation = path.methods.get(method);
                    boolean formatMatched = openapiOperation.responses.entrySet().stream()
                        .anyMatch(o ->
                        {
                            OpenapiResponseView content = o.getValue();
                            return "202".equals(o.getKey()) && content.headers.entrySet().stream()
                                .anyMatch(c -> matchFormat(format, c.getValue()));
                        });

                    if (formatMatched)
                    {
                        operationId = path.methods.get(method).id;
                        break correlated;
                    }
                }
            }
        }

        return Optional.ofNullable(operationId);
    }

    private boolean isOpenapiOperationAsync(
        OpenapiOperationView openapiOperation)
    {
        return openapiOperation.responses.entrySet().stream()
            .anyMatch(o ->
            {
                OpenapiResponseView content = o.getValue();
                return "202".equals(o.getKey()) && content.headers.entrySet().stream()
                    .anyMatch(c -> hasCorrelationId(c.getValue()));
            });
    }

    private List<String> findParams(
        String item)
    {
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = parameters.reset(item);
        while (matcher.find())
        {
            paramNames.add(parameters.group(1));
        }
        return paramNames;
    }

    private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
        RouteConfigBuilder<C> route,
        OpenapiView openapi,
        OpenapiOperationView httpOperation,
        AsyncapiOperationView kafkaOperation,
        List<String> paramNames)
    {
        switch (kafkaOperation.action)
        {
        case "receive":
            route.with(HttpKafkaWithConfig::builder)
                .fetch()
                    .topic(kafkaOperation.channel.address)
                    .inject(with -> injectHttpKafkaRouteFetchWith(with, openapi, httpOperation, paramNames))
                    .build()
                .build();
            break;
        case "send":
            route.with(HttpKafkaWithConfig::builder)
                .produce()
                    .topic(kafkaOperation.channel.address)
                    .inject(w -> injectHttpKafkaRouteProduceWith(w, httpOperation, kafkaOperation, paramNames))
                    .build()
                .build();
            break;
        }

        return route;
    }

    private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
        HttpKafkaWithFetchConfigBuilder<C> fetch,
        OpenapiView openapi,
        OpenapiOperationView operation,
        List<String> paramNames)
    {
        merge:
        for (Map.Entry<String, OpenapiResponseView> response : operation.responses.entrySet())
        {
            OpenapiSchemaView schema = resolveSchemaForJsonContentType(response.getValue().content, openapi);

            if (schema != null && "array".equals(schema.type))
            {
                fetch.merged(HttpKafkaWithFetchMergeConfig.builder()
                    .contentType("application/json")
                    .initial("[]")
                    .path("/-")
                    .build());
                break merge;
            }
        }

        if (!paramNames.isEmpty())
        {
            fetch.filters(List.of(HttpKafkaWithFetchFilterConfig.builder()
                .key(String.format("${params.%s}", paramNames.get(paramNames.size() - 1)))
                .build()));
        }

        return fetch;
    }

    private <C> HttpKafkaWithProduceConfigBuilder<C> injectHttpKafkaRouteProduceWith(
        HttpKafkaWithProduceConfigBuilder<C> produce,
        OpenapiOperationView openapiOperation,
        AsyncapiOperationView asyncapiOperation,
        List<String> paramNames)
    {
        final String key = !paramNames.isEmpty() ? String.format("${params.%s}",
                paramNames.get(paramNames.size() - 1)) : "${idempotencyKey}";

        produce.acks("in_sync_replicas").key(key);

        for (Map.Entry<String, OpenapiResponseView> response : openapiOperation.responses.entrySet())
        {
            if ("202".equals(response.getKey()))
            {
                OpenapiResponseView content = response.getValue();
                boolean async = content.headers.entrySet().stream()
                    .anyMatch(e -> hasCorrelationId(e.getValue()));

                if (async)
                {
                    content.headers.forEach((k, v) ->
                    {
                        String location = v.schema.format;
                        location = location.replaceAll(CORRELATION_ID, "\\${correlationId}");
                        location = location.replaceAll(PARAMETERS, "\\${params.$1}");
                        produce.async(HttpKafkaWithProduceAsyncHeaderConfig.builder()
                            .name(k)
                            .value(location)
                            .build());
                    });
                }
            }
        }
        AsyncapiReplyView reply = asyncapiOperation.reply;
        if (reply != null)
        {
            produce.replyTo(reply.channel.address);
        }

        produce.build();

        return produce;
    }

    private boolean hasCorrelationId(
        OpenapiHeaderView header)
    {
        boolean hasCorrelationId = false;
        OpenapiSchemaView schema = header.schema;
        if (schema != null &&
            schema.format != null)
        {
            hasCorrelationId = correlation.reset(schema.format).find();
        }
        return hasCorrelationId;
    }

    private boolean matchFormat(
        String format,
        OpenapiHeaderView header)
    {
        boolean matched = false;
        OpenapiSchemaView schema = header.schema;
        if (schema != null &&
            schema.format != null)
        {
            matched = schema.format.equals(format);
        }

        return matched;
    }

    private <C> NamespaceConfigBuilder<C> injectNamespaceMetric(
         NamespaceConfigBuilder<C> namespace,
        boolean hasMetrics)
    {
        if (hasMetrics)
        {
            namespace
                .telemetry()
                    .metric()
                        .group("stream")
                        .name("stream.active.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.active.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.opens.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.opens.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.data.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.data.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.errors.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.errors.sent")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.closes.received")
                        .build()
                    .metric()
                        .group("stream")
                        .name("stream.closes.sent")
                        .build()
                    .build();
        }

        return namespace;
    }

    protected  <C> BindingConfigBuilder<C> injectMetrics(
        BindingConfigBuilder<C> binding,
        List<MetricRefConfig> metricRefs,
        String protocol)
    {
        List<MetricRefConfig> metrics = metricRefs.stream()
            .filter(m -> m.name.startsWith("stream."))
            .collect(toList());

        if (!metrics.isEmpty())
        {
            final TelemetryRefConfigBuilder<BindingConfigBuilder<C>> telemetry = binding.telemetry();
            metrics.forEach(telemetry::metric);
            telemetry.build();
        }

        return binding;
    }

    private OpenapiSchemaView resolveSchemaForJsonContentType(
        Map<String, OpenapiMediaTypeView> content,
        OpenapiView openApi)
    {
        OpenapiMediaTypeView response = null;
        if (content != null)
        {
            for (String contentType : content.keySet())
            {
                if (jsonContentType.reset(contentType).matches())
                {
                    response = content.get(contentType);
                    break;
                }
            }
        }

        return response == null ? null : response.schema;
    }
}
