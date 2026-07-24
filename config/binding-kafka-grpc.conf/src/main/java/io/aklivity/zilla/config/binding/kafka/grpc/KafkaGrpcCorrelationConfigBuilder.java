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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class KafkaGrpcCorrelationConfigBuilder<T> extends ConfigBuilder<T, KafkaGrpcCorrelationConfigBuilder<T>>
{
    private final Function<KafkaGrpcCorrelationConfig, T> mapper;
    private String correlationId;
    private String service;
    private String method;
    private String replyTo;

    KafkaGrpcCorrelationConfigBuilder(
        Function<KafkaGrpcCorrelationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaGrpcCorrelationConfigBuilder<T>> thisType()
    {
        return (Class<KafkaGrpcCorrelationConfigBuilder<T>>) getClass();
    }

    public KafkaGrpcCorrelationConfigBuilder<T> correlationId(
        String correlationId)
    {
        this.correlationId = correlationId;
        return this;
    }

    public KafkaGrpcCorrelationConfigBuilder<T> service(
        String service)
    {
        this.service = service;
        return this;
    }

    public KafkaGrpcCorrelationConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    public KafkaGrpcCorrelationConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaGrpcCorrelationConfig(correlationId, service, method, replyTo));
    }
}
