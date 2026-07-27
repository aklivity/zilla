/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.binding.kafka.grpc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class KafkaGrpcConditionConfigBuilder<T> extends ConfigBuilder<T, KafkaGrpcConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;
    private String topic;
    private String key;
    private Map<String, String> headers;
    private String replyTo;
    private String service;
    private String method;

    KafkaGrpcConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaGrpcConditionConfigBuilder<T>> thisType()
    {
        return (Class<KafkaGrpcConditionConfigBuilder<T>>) getClass();
    }

    public KafkaGrpcConditionConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> headers(
        Map<String, String> headers)
    {
        this.headers = headers;
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> header(
        String name,
        String value)
    {
        if (headers == null)
        {
            headers = new LinkedHashMap<>();
        }
        headers.put(name, value);
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> service(
        String service)
    {
        this.service = service;
        return this;
    }

    public KafkaGrpcConditionConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaGrpcConditionConfig(topic, replyTo, key, headers, service, method));
    }
}
