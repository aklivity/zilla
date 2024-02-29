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
package io.aklivity.zilla.runtime.binding.http.kafka.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithProduceConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaWithProduceConfigBuilder<T>>
{
    private final Function<HttpKafkaWithProduceConfig, T> mapper;
    private String topic;
    private KafkaAckMode acks;
    private String key;
    private List<HttpKafkaWithProduceOverrideConfig> overrides;
    private String replyTo;
    private List<HttpKafkaWithProduceAsyncHeaderConfig> async;


    HttpKafkaWithProduceConfigBuilder(
        Function<HttpKafkaWithProduceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithProduceConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public HttpKafkaWithProduceConfigBuilder<T> acks(
        String acks)
    {
        this.acks = KafkaAckMode.valueOf(acks.toUpperCase());
        return this;
    }

    public HttpKafkaWithProduceConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public HttpKafkaWithProduceConfigBuilder<T> overrides(
        List<HttpKafkaWithProduceOverrideConfig> overrides)
    {
        this.overrides = overrides;
        return this;
    }

    public HttpKafkaWithProduceConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    public HttpKafkaWithProduceConfigBuilder<T> async(
        List<HttpKafkaWithProduceAsyncHeaderConfig> async)
    {
        this.async = async;
        return this;
    }


    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithProduceConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithProduceConfigBuilder<T>>) getClass();
    }


    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithProduceConfig(topic, acks, key, overrides, replyTo, async));
    }
}
