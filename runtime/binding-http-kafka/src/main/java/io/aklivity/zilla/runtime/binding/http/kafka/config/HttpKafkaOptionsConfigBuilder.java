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
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class HttpKafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private HttpKafkaCorrelationConfig correlation;
    private HttpKafkaIdempotencyConfig idempotency;

    HttpKafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaOptionsConfigBuilder<T> correlation(
        HttpKafkaCorrelationConfig correlation)
    {
        this.correlation = correlation;
        return this;
    }

    public HttpKafkaOptionsConfigBuilder<T> idempotency(
        HttpKafkaIdempotencyConfig idempotency)
    {
        this.idempotency = idempotency;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaOptionsConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpKafkaOptionsConfig(idempotency, correlation));
    }
}
