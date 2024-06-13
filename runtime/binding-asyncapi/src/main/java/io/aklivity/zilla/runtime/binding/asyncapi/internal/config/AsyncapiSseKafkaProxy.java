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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public class AsyncapiSseKafkaProxy extends AsyncapiProxy
{
    private static final String ASYNCAPI_KAFKA_PROTOCOL_NAME = "kafka";
    private static final String ASYNCAPI_SSE_PROTOCOL_NAME = "sse";
    private static final String ASYNCAPI_RECEIVE_ACTION_NAME = "receive";
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private final Matcher parameters = PARAMETER_PATTERN.matcher("");

    protected AsyncapiSseKafkaProxy(
        String qname,
        Map<String, Asyncapi> asyncapis)
    {
        super("sse-kafka", qname, asyncapis);
    }

    @Override
    protected <C> BindingConfigBuilder<C> injectProxyRoutes(
        BindingConfigBuilder<C> binding,
        List<AsyncapiRouteConfig> routes)
    {
        inject:
        for (AsyncapiRouteConfig route : routes)
        {
            final Asyncapi kafkaAsyncapi = asyncapis.get(route.with.apiId);

            for (AsyncapiConditionConfig condition : route.when)
            {
                final Asyncapi sseAsyncapi = asyncapis.get(condition.apiId);
                if (sseAsyncapi.servers.values().stream().anyMatch(s -> !s.protocol.startsWith(ASYNCAPI_SSE_PROTOCOL_NAME)))
                {
                    break inject;
                }
                final AsyncapiOperation whenOperation = sseAsyncapi.operations.get(condition.operationId);
                if (whenOperation == null)
                {
                    for (Map.Entry<String, AsyncapiOperation> e : sseAsyncapi.operations.entrySet())
                    {
                        AsyncapiOperation withOperation = route.with.operationId != null ?
                            kafkaAsyncapi.operations.get(route.with.operationId) : kafkaAsyncapi.operations.get(e.getKey());
                        if (withOperation != null)
                        {
                            binding = addSseKafkaRoute(binding, kafkaAsyncapi, sseAsyncapi, e.getValue(), withOperation);
                        }
                    }
                }
                else
                {
                    AsyncapiOperation withOperation = kafkaAsyncapi.operations.get(route.with.operationId);
                    binding = addSseKafkaRoute(binding, kafkaAsyncapi, sseAsyncapi, whenOperation, withOperation);
                }
            }
        }
        return binding;
    }

    private <C> BindingConfigBuilder<C> addSseKafkaRoute(
        BindingConfigBuilder<C> binding,
        Asyncapi kafkaAsyncapi,
        Asyncapi sseAsyncapi,
        AsyncapiOperation whenOperation,
        AsyncapiOperation withOperation)
    {

        final AsyncapiChannelView channel = AsyncapiChannelView.of(sseAsyncapi.channels, whenOperation.channel);
        String path = channel.address();
        final List<String> paramNames = findParams(path);

        final RouteConfigBuilder<BindingConfigBuilder<C>> routeBuilder = binding.route();
        routeBuilder
            .exit(qname)
            .when(SseKafkaConditionConfig::builder)
                .path(path)
                .build()
            .inject(r -> injectSseKafkaRouteWith(r, kafkaAsyncapi, withOperation));
        binding = routeBuilder.build();
        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProxyOptions(
        BindingConfigBuilder<C> binding,
        AsyncapiOptionsConfig options)
    {
        return binding;
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

    private <C> RouteConfigBuilder<C> injectSseKafkaRouteWith(
        RouteConfigBuilder<C> route,
        Asyncapi kafkaAsyncapi,
        AsyncapiOperation kafkaOperation)
    {
        final SseKafkaWithConfigBuilder<SseKafkaWithConfig> newWith = SseKafkaWithConfig.builder();
        final AsyncapiChannelView channel = AsyncapiChannelView
            .of(kafkaAsyncapi.channels, kafkaOperation.channel);
        final String topic = channel.address();

        if (ASYNCAPI_RECEIVE_ACTION_NAME.equals(kafkaOperation.action))
        {
            newWith
                .topic(topic)
                .build();
        }

        route.with(newWith.build());

        return route;
    }
}
