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

public final class HttpKafkaWithProduceAsyncHeaderConfig
{
    public final String name;
    public final String value;

    public HttpKafkaWithProduceAsyncHeaderConfig(
        String name,
        String value)
    {
        this.name = name;
        this.value = value;
    }

    public static HttpKafkaWithProduceAsyncHeaderConfigBuilder<HttpKafkaWithProduceAsyncHeaderConfig> builder()
    {
        return new HttpKafkaWithProduceAsyncHeaderConfigBuilder<>(HttpKafkaWithProduceAsyncHeaderConfig.class::cast);
    }

    public static <T> HttpKafkaWithProduceAsyncHeaderConfigBuilder<T> builder(
        Function<HttpKafkaWithProduceAsyncHeaderConfig, T> mapper)
    {
        return new HttpKafkaWithProduceAsyncHeaderConfigBuilder<>(mapper);
    }
}
