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

public final class HttpKafkaWithProduceOverrideConfig
{
    public final String name;
    public final String value;

    public HttpKafkaWithProduceOverrideConfig(
        String name,
        String value)
    {
        this.name = name;
        this.value = value;
    }

    public static HttpKafkaWithProduceOverrideConfigBuilder<HttpKafkaWithProduceOverrideConfig> builder()
    {
        return new HttpKafkaWithProduceOverrideConfigBuilder<>(HttpKafkaWithProduceOverrideConfig.class::cast);
    }

    public static <T> HttpKafkaWithProduceOverrideConfigBuilder<T> builder(
        Function<HttpKafkaWithProduceOverrideConfig, T> mapper)
    {
        return new HttpKafkaWithProduceOverrideConfigBuilder<>(mapper);
    }
}
