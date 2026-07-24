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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class GrpcKafkaWithProduceConfigBuilder<T> extends ConfigBuilder<T, GrpcKafkaWithProduceConfigBuilder<T>>
{
    private final Function<GrpcKafkaWithProduceConfig, T> mapper;
    private String topic;
    private String acks;
    private String key;
    private List<GrpcKafkaWithProduceOverrideConfig> overrides;
    private String replyTo;

    GrpcKafkaWithProduceConfigBuilder(
        Function<GrpcKafkaWithProduceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcKafkaWithProduceConfigBuilder<T>> thisType()
    {
        return (Class<GrpcKafkaWithProduceConfigBuilder<T>>) getClass();
    }

    public GrpcKafkaWithProduceConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public GrpcKafkaWithProduceConfigBuilder<T> acks(
        String acks)
    {
        this.acks = acks;
        return this;
    }

    public GrpcKafkaWithProduceConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public GrpcKafkaWithProduceConfigBuilder<T> overrides(
        List<GrpcKafkaWithProduceOverrideConfig> overrides)
    {
        this.overrides = overrides;
        return this;
    }

    public GrpcKafkaWithProduceOverrideConfigBuilder<GrpcKafkaWithProduceConfigBuilder<T>> override()
    {
        return GrpcKafkaWithProduceOverrideConfig.builder(this::override);
    }

    public GrpcKafkaWithProduceConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcKafkaWithProduceConfig(topic, acks, key, overrides, replyTo));
    }

    private GrpcKafkaWithProduceConfigBuilder<T> override(
        GrpcKafkaWithProduceOverrideConfig override)
    {
        if (overrides == null)
        {
            overrides = new LinkedList<>();
        }

        overrides.add(override);
        return this;
    }
}
