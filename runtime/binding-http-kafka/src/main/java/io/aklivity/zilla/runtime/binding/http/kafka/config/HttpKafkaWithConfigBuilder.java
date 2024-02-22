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

import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaCapability;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class HttpKafkaWithConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;
    private HttpKafkaCapability capability;
    private HttpKafkaWithFetchConfig fetch;
    private HttpKafkaWithProduceConfig produce;

    public HttpKafkaWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaWithConfigBuilder<T> fetch(
        HttpKafkaWithFetchConfig fetch)
    {
        capability = HttpKafkaCapability.FETCH;
        this.fetch = fetch;
        return this;
    }

    public HttpKafkaWithConfigBuilder<T> produce(
        HttpKafkaWithProduceConfig produce)
    {
        capability = HttpKafkaCapability.PRODUCE;
        this.produce = produce;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaWithConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaWithConfigBuilder<T>>) getClass();
    }


    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaWithConfig(capability, fetch, produce));
    }
}
