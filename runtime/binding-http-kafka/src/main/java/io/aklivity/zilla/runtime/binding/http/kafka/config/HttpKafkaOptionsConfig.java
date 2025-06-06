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

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class HttpKafkaOptionsConfig extends OptionsConfig
{
    public final HttpKafkaIdempotencyConfig idempotency;
    public final HttpKafkaCorrelationConfig correlation;

    public HttpKafkaOptionsConfig(
        HttpKafkaIdempotencyConfig idempotency,
        HttpKafkaCorrelationConfig correlation)
    {
        this.idempotency = idempotency;
        this.correlation = correlation;
    }

    public static HttpKafkaOptionsConfigBuilder<HttpKafkaOptionsConfig> builder()
    {
        return new HttpKafkaOptionsConfigBuilder<>(HttpKafkaOptionsConfig.class::cast);
    }

    public static <T> HttpKafkaOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new HttpKafkaOptionsConfigBuilder<>(mapper);
    }
}
