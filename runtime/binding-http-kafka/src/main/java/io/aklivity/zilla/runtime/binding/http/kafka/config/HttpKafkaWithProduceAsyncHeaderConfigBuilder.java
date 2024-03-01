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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaWithProduceAsyncHeaderConfigBuilder<T> extends
    ConfigBuilder<T, HttpKafkaWithProduceAsyncHeaderConfigBuilder<T>>
{
    private final Function<HttpKafkaWithProduceAsyncHeaderConfig, T> mapper;
    private String name;
    private String value;


    HttpKafkaWithProduceAsyncHeaderConfigBuilder(
        Function<HttpKafkaWithProduceAsyncHeaderConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithProduceAsyncHeaderConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public HttpKafkaWithProduceAsyncHeaderConfigBuilder<T> value(
        String value)
    {
        this.value = value;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithProduceAsyncHeaderConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithProduceAsyncHeaderConfigBuilder<T>>) getClass();
    }


    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithProduceAsyncHeaderConfig(name, value));
    }
}
