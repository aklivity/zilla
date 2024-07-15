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

import static io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig.EVENT_ID_DEFAULT;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiKafkaFilter;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSecurityScheme;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSecuritySchemeView;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.sse.kafka.config.SseKafkaWithFilterConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.GuardedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public class AsyncapiSseKafkaProxy extends AsyncapiProxy
{
    private static final String ASYNCAPI_KAFKA_PROTOCOL_NAME = "kafka";
    private static final String ASYNCAPI_SSE_PROTOCOL_NAME = "sse";
    private static final String ASYNCAPI_RECEIVE_ACTION_NAME = "receive";

    protected AsyncapiSseKafkaProxy(
        String qname,
        Map<String, Asyncapi> asyncapis)
    {
        super("sse-kafka", qname, asyncapis);
    }

    @Override
    protected <C> BindingConfigBuilder<C> injectProxyRoutes(
        BindingConfigBuilder<C> binding,
        String namespace,
        List<AsyncapiRouteConfig> routes)
    {
        inject:
        for (AsyncapiRouteConfig route : routes)
        {
            final Asyncapi kafkaAsyncapi = asyncapis.get(route.with.apiId);

            for (AsyncapiConditionConfig condition : route.when)
            {
                final Asyncapi sseAsyncapi = asyncapis.get(condition.apiId);
                if (sseAsyncapi.servers.values().stream().noneMatch(s -> s.protocol.startsWith(ASYNCAPI_SSE_PROTOCOL_NAME)))
                {
                    break inject;
                }

                final AsyncapiOperation whenOperation = sseAsyncapi.operations.get(condition.operationId);
                if (whenOperation == null)
                {
                    for (Map.Entry<String, AsyncapiOperation> e : sseAsyncapi.operations.entrySet())
                    {
                        AsyncapiOperation withOperation = route.with.operationId != null
                            ? kafkaAsyncapi.operations.get(route.with.operationId)
                            : kafkaAsyncapi.operations.get(e.getKey());

                        if (withOperation != null)
                        {
                            binding = addSseKafkaRoute(binding, kafkaAsyncapi, sseAsyncapi, e.getValue(), withOperation,
                                    namespace);
                        }
                    }
                }
                else
                {
                    AsyncapiOperation withOperation = kafkaAsyncapi.operations.get(route.with.operationId);
                    binding = addSseKafkaRoute(binding, kafkaAsyncapi, sseAsyncapi, whenOperation, withOperation, namespace);
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
        AsyncapiOperation withOperation,
        String namespace)
    {
        if (whenOperation.bindings == null || !whenOperation.bindings.containsKey("http"))
        {
            final AsyncapiChannelView channel = AsyncapiChannelView.of(sseAsyncapi.channels, whenOperation.channel);
            String path = channel.address();

            binding.route()
                .exit(qname)
                .when(SseKafkaConditionConfig::builder)
                    .path(path)
                    .build()
                .inject(r -> injectSseKafkaRouteWith(r, kafkaAsyncapi, whenOperation, withOperation, namespace))
                .inject(route -> injectSseServerRouteGuarded(
                    route, namespace, sseAsyncapi, whenOperation.security))
                .build();
        }

        return binding;
    }

    @Override
    public <C> BindingConfigBuilder<C> injectProxyOptions(
        BindingConfigBuilder<C> binding,
        AsyncapiOptionsConfig options)
    {
        return binding;
    }

    private <C> RouteConfigBuilder<C> injectSseKafkaRouteWith(
        RouteConfigBuilder<C> route,
        Asyncapi kafkaAsyncapi,
        AsyncapiOperation sseOperation,
        AsyncapiOperation kafkaOperation,
        String namespace)
    {
        if (ASYNCAPI_RECEIVE_ACTION_NAME.equals(kafkaOperation.action))
        {
            final AsyncapiChannelView channel = AsyncapiChannelView
                    .of(kafkaAsyncapi.channels, kafkaOperation.channel);
            final String topic = channel.address();

            route.with(SseKafkaWithConfig.builder()
                    .topic(topic)
                    .eventId(EVENT_ID_DEFAULT)
                    .inject(w -> injectSseKafkaRouteWithFilters(w, sseOperation, namespace))
                    .build());
        }

        return route;
    }

    private <C> SseKafkaWithConfigBuilder<C> injectSseKafkaRouteWithFilters(
        SseKafkaWithConfigBuilder<C> with,
        AsyncapiOperation sseOperation,
        String namespace)
    {
        if (sseOperation.bindings != null)
        {
            AsyncapiBinding sseKafkaBinding = sseOperation.bindings.get("x-zilla-sse-kafka");
            if (sseKafkaBinding != null)
            {
                List<AsyncapiKafkaFilter> filters = sseKafkaBinding.filters;
                if (filters != null)
                {
                    for (AsyncapiKafkaFilter filter : filters)
                    {
                        SseKafkaWithFilterConfigBuilder<SseKafkaWithConfigBuilder<C>> withFilter =
                                with.filter();

                        String key = filter.key;
                        if (key != null)
                        {
                            key = AsyncapiIdentity.resolve(namespace, key);

                            withFilter.key(key);
                        }

                        Map<String, String> headers = filter.headers;
                        if (headers != null)
                        {
                            for (Map.Entry<String, String> header : headers.entrySet())
                            {
                                String name = header.getKey();
                                String value = header.getValue();

                                value = AsyncapiIdentity.resolve(namespace, value);

                                withFilter.header()
                                    .name(name)
                                    .value(value)
                                    .build();
                            }
                        }

                        withFilter.build();
                    }
                }
            }
        }

        return with;
    }

    private <C> RouteConfigBuilder<C> injectSseServerRouteGuarded(
        RouteConfigBuilder<C> route,
        String namespace,
        Asyncapi asyncapi,
        List<AsyncapiSecurityScheme> securities)
    {
        if (securities != null && !securities.isEmpty())
        {
            AsyncapiSecuritySchemeView security =
                AsyncapiSecuritySchemeView.of(asyncapi.components.securitySchemes, securities.get(0));

            if ("oauth2".equals(security.type()))
            {
                route
                    .guarded()
                    .name(String.format("%s:jwt0", namespace))
                    .inject(guarded -> injectGuardedRoles(guarded, security.scopes()))
                    .build();
            }
        }
        return route;
    }

    private <C> GuardedConfigBuilder<C> injectGuardedRoles(
        GuardedConfigBuilder<C> guarded,
        List<String> roles)
    {
        for (String role : roles)
        {
            guarded.role(role);
        }
        return guarded;
    }
}
