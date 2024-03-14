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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiResponse;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiResponseByContentType;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiPathView;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiSchemaView;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public final class OpenapiAsyncCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private static final Pattern JSON_CONTENT_TYPE = Pattern.compile("^application/(?:.+\\+)?json$");
    private static final Pattern PATH_ID_PATTERN = Pattern.compile("\\{id\\}");

    private final Matcher jsonContentType = JSON_CONTENT_TYPE.matcher("");
    private final Matcher pathId = PATH_ID_PATTERN.matcher("");

    @Override
    public String type()
    {
        return OpenapiAsyncapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        OpenapiAsyncapiOptionsConfig options = (OpenapiAsyncapiOptionsConfig) binding.options;

        List<OpenapiAsyncapiRouteConfig> routes = binding.routes.stream()
            .map(r -> new OpenapiAsyncapiRouteConfig(r, options::resolveOpenapiApiId))
            .collect(toList());

        return BindingConfig.builder(binding)
                .composite()
                    .name(String.format("%s/http_kafka", binding.qname))
                    .binding()
                        .name("http_kafka0")
                        .type("http-kafka")
                        .kind(PROXY)
                        .inject(b -> this.injectHttpKafkaRoutes(b, binding.qname, options.specs, routes))
                        .build()
                    .build()
                .build();
    }

    private <C> BindingConfigBuilder<C> injectHttpKafkaRoutes(
        BindingConfigBuilder<C> binding,
        String qname,
        OpenapiAsyncapiSpecConfig spec,
        List<OpenapiAsyncapiRouteConfig> routes)
    {
        for (OpenapiAsyncapiRouteConfig route : routes)
        {
            for (OpenapiAsyncapiConditionConfig condition : route.when)
            {
                Openapi openapi = spec.openapi.stream()
                    .filter(o -> o.apiLabel.equals(condition.apiId))
                    .findFirst()
                    .get().openapi;
                Asyncapi asyncapi = spec.asyncapi.stream()
                    .filter(o -> o.apiLabel.equals(route.with.apiId))
                    .findFirst()
                    .get().asyncapi;

                for (String item : openapi.paths.keySet())
                {
                    OpenapiPathView path = OpenapiPathView.of(openapi.paths.get(item));
                    for (String method : path.methods().keySet())
                    {
                        final String operationId = condition.operationId != null ?
                            condition.operationId : path.methods().get(method).operationId;

                        final OpenapiOperation openapiOperation = path.methods().get(method);

                        final AsyncapiOperation asyncapiOperation = asyncapi.operations.entrySet().stream()
                            .filter(f -> f.getKey().equals(operationId))
                            .map(v -> v.getValue())
                            .findFirst()
                            .get();

                        final AsyncapiChannelView channel = AsyncapiChannelView
                            .of(asyncapi.channels, asyncapiOperation.channel);

                        final boolean includeKey = pathId.reset(item).find();

                        binding
                            .route()
                                .exit(qname)
                                .when(HttpKafkaConditionConfig::builder)
                                    .method(method)
                                    .path(item)
                                    .build()
                                .inject(r -> injectHttpKafkaRouteWith(r, openapi, openapiOperation,
                                    asyncapiOperation.action, channel.address(), includeKey))
                                .build();
                    }
                }
            }
        }

        return binding;
    }

    private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
        RouteConfigBuilder<C> route,
        Openapi openapi,
        OpenapiOperation operation,
        String action,
        String address,
        boolean includeKey)
    {
        final HttpKafkaWithConfigBuilder<HttpKafkaWithConfig> newWith = HttpKafkaWithConfig.builder();

        switch (action)
        {
        case "receive":
            newWith.fetch(HttpKafkaWithFetchConfig.builder()
                .topic(address)
                .inject(with -> this.injectHttpKafkaRouteFetchWith(with, openapi, operation, includeKey))
                .build());
            break;
        case "send":
            newWith.produce(HttpKafkaWithProduceConfig.builder()
                .topic(address)
                .acks("in_sync_replicas")
                .build());
            break;
        }

        route.with(newWith.build());

        return route;
    }

    private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
        HttpKafkaWithFetchConfigBuilder<C> fetch,
        Openapi openapi,
        OpenapiOperation operation,
        boolean includeKey)
    {
        merge:
        for (Map.Entry<String, OpenapiResponseByContentType> response : operation.responses.entrySet())
        {
            OpenapiSchemaView schema = resolveSchemaForJsonContentType(response.getValue().content, openapi);

            if ("array".equals(schema.getType()))
            {
                fetch.merged(HttpKafkaWithFetchMergeConfig.builder()
                    .contentType("application/json")
                    .initial("[]")
                    .path("/-")
                    .build());
                break merge;
            }
        }

        if (includeKey)
        {
            fetch.filters(List.of(HttpKafkaWithFetchFilterConfig.builder()
                .key("${params.id}")
                .build()));
        }

        return fetch;
    }

    private OpenapiSchemaView resolveSchemaForJsonContentType(
        Map<String, OpenapiResponse> content,
        Openapi openApi)
    {
        OpenapiResponse response = null;
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

        return response == null ? null : OpenapiSchemaView.of(openApi.components.schemas, response.schema);
    }
}
