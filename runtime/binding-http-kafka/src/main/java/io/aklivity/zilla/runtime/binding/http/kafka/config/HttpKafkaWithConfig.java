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

import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.config.HttpKafkaCapability;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class HttpKafkaWithConfig extends WithConfig
{
    public final HttpKafkaCapability capability;
    public final Optional<HttpKafkaWithFetchConfig> fetch;
    public final Optional<HttpKafkaWithProduceConfig> produce;

    public static HttpKafkaWithConfigBuilder<HttpKafkaWithConfig> builder()
    {
        return new HttpKafkaWithConfigBuilder<>(HttpKafkaWithConfig.class::cast);
    }

    public static <T> HttpKafkaWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new HttpKafkaWithConfigBuilder<>(mapper);
    }

    HttpKafkaWithConfig(
        long compositeId,
        HttpKafkaCapability capability,
        HttpKafkaWithFetchConfig fetch,
        HttpKafkaWithProduceConfig produce)
    {
        super(compositeId);
        this.capability = capability;
        this.fetch = Optional.ofNullable(fetch);
        this.produce = Optional.ofNullable(produce);
    }
}
