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
package io.aklivity.zilla.config.binding.grpc.kafka;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcKafkaCorrelationConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaCorrelationConfigBuilder<T>>
{
    private final Function<GrpcKafkaCorrelationConfig, T> mapper;
    private String correlationId;
    private String service;
    private String method;
    private String replyTo;

    GrpcKafkaCorrelationConfigBuilder(
        Function<GrpcKafkaCorrelationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaCorrelationConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaCorrelationConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaCorrelationConfigBuilder<T> correlationId(
        String correlationId)
    {
        this.correlationId = correlationId;
        return this;
    }

    public GrpcKafkaCorrelationConfigBuilder<T> service(
        String service)
    {
        this.service = service;
        return this;
    }

    public GrpcKafkaCorrelationConfigBuilder<T> method(
        String method)
    {
        this.method = method;
        return this;
    }

    public GrpcKafkaCorrelationConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaCorrelationConfig(correlationId, service, method, replyTo));
    }
}
