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
package io.aklivity.zilla.config.binding.http.kafka;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class HttpKafkaWithProduceConfig
{
    public final String topic;
    public final String acks;
    public final Optional<String> key;
    public final Optional<List<HttpKafkaWithProduceOverrideConfig>> overrides;
    public final Optional<String> replyTo;
    public final Optional<List<HttpKafkaWithProduceAsyncHeaderConfig>> async;
    public final String correlationId;

    HttpKafkaWithProduceConfig(
        String topic,
        String acks,
        String key,
        List<HttpKafkaWithProduceOverrideConfig> overrides,
        String replyTo,
        String correlationId,
        List<HttpKafkaWithProduceAsyncHeaderConfig> async)
    {
        this.topic = topic;
        this.acks = acks;
        this.key = Optional.ofNullable(key);
        this.overrides = Optional.ofNullable(overrides);
        this.replyTo = Optional.ofNullable(replyTo);
        this.correlationId = correlationId;
        this.async = Optional.ofNullable(async);
    }

    public static HttpKafkaWithProduceConfigBuilder<HttpKafkaWithProduceConfig> builder()
    {
        return new HttpKafkaWithProduceConfigBuilder<>(HttpKafkaWithProduceConfig.class::cast);
    }

    public static <T> HttpKafkaWithProduceConfigBuilder<T> builder(
        Function<HttpKafkaWithProduceConfig, T> mapper)
    {
        return new HttpKafkaWithProduceConfigBuilder<>(mapper);
    }
}
