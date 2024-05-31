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
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiChannel;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMessage;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiOperation;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiReply;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiChannelView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiCorrelationIdView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiMessageView;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiSchemaView;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfigBuilder;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.BindingConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.RouteConfigBuilder;

public class AsyncapiHttpKafkaProxy extends AsyncapiProxy
{
    private static final String CORRELATION_ID = "\\{correlationId\\}";
    private static final String PARAMETERS = "\\{(?!correlationId)(\\w+)\\}";
    private static final String HEADER_LOCATION = "([^/]+)$";
    private static final String ASYNCAPI_KAFKA_PROTOCOL_NAME = "kafka";
    private static final String ASYNCAPI_HTTP_PROTOCOL_NAME = "http";
    private static final String ASYNCAPI_SEND_ACTION_NAME = "send";
    private static final String ASYNCAPI_RECEIVE_ACTION_NAME = "receive";
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern CORRELATION_PATTERN = Pattern.compile(CORRELATION_ID);
    private static final Pattern HEADER_LOCATION_PATTERN = Pattern.compile(HEADER_LOCATION);

    private final Matcher correlation = CORRELATION_PATTERN.matcher("");
    private final Matcher parameters = PARAMETER_PATTERN.matcher("");
    private final Matcher headerLocation = HEADER_LOCATION_PATTERN.matcher("");

    protected AsyncapiHttpKafkaProxy(
        String qname,
        Map<String, Asyncapi> asyncapis)
    {
        super("http-kafka", qname, asyncapis);
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

            final AsyncapiOperation withOperation = kafkaAsyncapi.operations.get(route.with.operationId);

            for (AsyncapiConditionConfig condition : route.when)
            {
                final Asyncapi httpAsyncapi = asyncapis.get(condition.apiId);
                if (httpAsyncapi.servers.values().stream().anyMatch(s -> !s.protocol.startsWith(ASYNCAPI_HTTP_PROTOCOL_NAME)))
                {
                    break inject;
                }
                final AsyncapiOperation whenOperation = httpAsyncapi.operations.get(condition.operationId);
                final AsyncapiChannelView channel = AsyncapiChannelView.of(httpAsyncapi.channels, whenOperation.channel);
                String path = channel.address();
                String method = whenOperation.bindings.get("http").method;
                final List<String> paramNames = findParams(path);

                AsyncapiChannelView httpChannel = AsyncapiChannelView.of(httpAsyncapi.channels, whenOperation.channel);

                boolean async = httpChannel.messages().values()
                    .stream().anyMatch(asyncapiMessage ->
                    {
                        AsyncapiMessageView message = AsyncapiMessageView.of(httpAsyncapi.components.messages, asyncapiMessage);
                        return message.correlationId() != null;
                    });

                if (async)
                {
                    for (AsyncapiOperation operation : httpAsyncapi.operations.values())
                    {
                        AsyncapiChannelView channelView = AsyncapiChannelView.of(httpAsyncapi.channels, operation.channel);
                        if (parameters.reset(channelView.address()).find())
                        {
                            AsyncapiReply reply = withOperation.reply;
                            if (reply != null)
                            {
                                final RouteConfigBuilder<BindingConfigBuilder<C>> asyncRouteBuilder = binding.route();
                                binding = addAsyncOperation(asyncRouteBuilder, httpAsyncapi, kafkaAsyncapi, operation,
                                    withOperation);
                            }
                        }
                    }
                }

                final RouteConfigBuilder<BindingConfigBuilder<C>> routeBuilder = binding.route();
                routeBuilder
                    .exit(qname)
                    .when(HttpKafkaConditionConfig::builder)
                        .method(method)
                        .path(path)
                        .build()
                    .inject(r -> injectHttpKafkaRouteWith(r, httpAsyncapi, kafkaAsyncapi, whenOperation,
                        withOperation, paramNames));
                binding = routeBuilder.build();
            }
        }
        return binding;
    }

    private <C> BindingConfigBuilder<C>  addAsyncOperation(
        RouteConfigBuilder<BindingConfigBuilder<C>> routeBuilder,
        Asyncapi httpAsyncapi,
        Asyncapi kafkaAsyncapi,
        AsyncapiOperation httpOperation,
        AsyncapiOperation kafkaOperation)
    {
        final AsyncapiChannelView channel = AsyncapiChannelView.of(httpAsyncapi.channels, httpOperation.channel);
        String path = channel.address();
        String method = httpOperation.bindings.get("http").method;
        final List<String> paramNames = findParams(path);
        return routeBuilder
            .exit(qname)
            .when(HttpKafkaConditionConfig::builder)
                .method(method)
                .path(path)
                .build()
            .inject(r -> injectAsyncProduceHttpKafkaRouteWith(r, httpAsyncapi, kafkaAsyncapi, httpOperation,
                kafkaOperation, paramNames))
            .build();
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

    private <C> RouteConfigBuilder<C> injectHttpKafkaRouteWith(
        RouteConfigBuilder<C> route,
        Asyncapi httpAsyncapi,
        Asyncapi kafkaAsyncapi,
        AsyncapiOperation httpOperation,
        AsyncapiOperation kafkaOperation,
        List<String> paramNames)
    {
        final HttpKafkaWithConfigBuilder<HttpKafkaWithConfig> newWith = HttpKafkaWithConfig.builder();
        final AsyncapiChannelView channel = AsyncapiChannelView
            .of(kafkaAsyncapi.channels, kafkaOperation.channel);
        final String topic = channel.address();

        switch (kafkaOperation.action)
        {
        case "receive":
            newWith.fetch(HttpKafkaWithFetchConfig.builder()
                .topic(topic)
                .inject(with -> injectHttpKafkaRouteFetchWith(with, httpAsyncapi, httpOperation, paramNames))
                .build());
            break;
        case "send":
            newWith.produce(HttpKafkaWithProduceConfig.builder()
                .topic(topic)
                .inject(w -> injectHttpKafkaRouteProduceWith(w, httpOperation, kafkaOperation, httpAsyncapi,
                    kafkaAsyncapi.channels, paramNames))
                .build());
            break;
        }

        route.with(newWith.build());

        return route;
    }

    private <C> RouteConfigBuilder<C> injectAsyncProduceHttpKafkaRouteWith(
        RouteConfigBuilder<C> route,
        Asyncapi httpAsyncapi,
        Asyncapi kafkaAsyncapi,
        AsyncapiOperation httpOperation,
        AsyncapiOperation kafkaOperation,
        List<String> paramNames)
    {
        final HttpKafkaWithConfigBuilder<HttpKafkaWithConfig> newWith = HttpKafkaWithConfig.builder();
        final AsyncapiChannelView channel = AsyncapiChannelView.of(kafkaAsyncapi.channels, kafkaOperation.channel);
        final String topic = channel.address();

        newWith.produce(HttpKafkaWithProduceConfig.builder()
            .topic(topic)
            .inject(w -> injectHttpKafkaRouteProduceWith(w, httpOperation, kafkaOperation, httpAsyncapi,
                kafkaAsyncapi.channels, paramNames))
            .build());
        route.with(newWith.build());

        return route;
    }

    private <C> HttpKafkaWithFetchConfigBuilder<C> injectHttpKafkaRouteFetchWith(
        HttpKafkaWithFetchConfigBuilder<C> fetch,
        Asyncapi httpAsyncapi,
        AsyncapiOperation httpOperation,
        List<String> paramNames)
    {
        final AsyncapiChannelView channel = AsyncapiChannelView.of(httpAsyncapi.channels, httpOperation.channel);
        merge:
        for (Map.Entry<String, AsyncapiMessage> message : channel.messages().entrySet())
        {
            AsyncapiMessageView messageView = AsyncapiMessageView.of(httpAsyncapi.components.messages, message.getValue());
            AsyncapiSchemaView schema = AsyncapiSchemaView.of(httpAsyncapi.components.schemas, messageView.payload());

            if (schema != null && "array".equals(schema.getType()))
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
        AsyncapiOperation httpOperation,
        AsyncapiOperation kafkaOperation,
        Asyncapi httpAsyncapi,
        Map<String, AsyncapiChannel> kafkaChannels,
        List<String> paramNames)
    {
        final String key = !paramNames.isEmpty() ? String.format("${params.%s}",
            paramNames.get(paramNames.size() - 1)) : "${idempotencyKey}";

        produce.acks("in_sync_replicas").key(key);

        AsyncapiChannelView httpChannel = AsyncapiChannelView.of(httpAsyncapi.channels, httpOperation.channel);

        httpChannel.messages().values()
            .forEach(asyncapiMessage ->
            {
                AsyncapiMessageView message = AsyncapiMessageView.of(httpAsyncapi.components.messages, asyncapiMessage);
                if (message.correlationId() != null)
                {
                    AsyncapiCorrelationIdView correlationId =
                        AsyncapiCorrelationIdView.of(httpAsyncapi.components.correlationIds, message.correlationId());
                    if (headerLocation.reset(correlationId.location()).find())
                    {
                        String headerName = headerLocation.group(1);
                        String location = message.headers().properties.get(headerName).format;
                        location = location.replaceAll(CORRELATION_ID, "\\${correlationId}");
                        location = location.replaceAll(PARAMETERS, "\\${params.$1}");
                        produce.async(HttpKafkaWithProduceAsyncHeaderConfig.builder()
                            .name("location")
                            .value(location)
                            .build());
                    }
                }
            });

        AsyncapiReply reply = kafkaOperation.reply;
        if (reply != null)
        {
            AsyncapiChannelView channel = AsyncapiChannelView.of(kafkaChannels, reply.channel);
            produce.replyTo(channel.address());
        }

        produce.build();

        return produce;
    }
}
