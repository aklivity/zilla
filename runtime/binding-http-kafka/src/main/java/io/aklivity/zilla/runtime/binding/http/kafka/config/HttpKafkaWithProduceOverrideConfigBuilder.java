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
package io.aklivity.zilla.runtime.binding.http.kafka.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithProduceOverrideConfigBuilder<T> extends
    ConfigBuilder<T, HttpKafkaWithProduceOverrideConfigBuilder<T>>
{
    private final Function<HttpKafkaWithProduceOverrideConfig, T> mapper;
    private String name;
    private String value;


    HttpKafkaWithProduceOverrideConfigBuilder(
        Function<HttpKafkaWithProduceOverrideConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithProduceOverrideConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public HttpKafkaWithProduceOverrideConfigBuilder<T> value(
        String value)
    {
        this.value = value;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithProduceOverrideConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithProduceOverrideConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithProduceOverrideConfig(name, value));
    }
}
