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

public final class HttpKafkaWithFetchMergeConfig
{
    public final String contentType;
    public final String initial;
    public final String path;

    public HttpKafkaWithFetchMergeConfig(
        String contentType,
        String initial,
        String path)
    {
        this.contentType = contentType;
        this.initial = initial;
        this.path = path;
    }

    public static HttpKafkaWithFetchMergeConfigBuilder<HttpKafkaWithFetchMergeConfig> builder()
    {
        return new HttpKafkaWithFetchMergeConfigBuilder<>(HttpKafkaWithFetchMergeConfig.class::cast);
    }

    public static <T> HttpKafkaWithFetchMergeConfigBuilder<T> builder(
        Function<HttpKafkaWithFetchMergeConfig, T> mapper)
    {
        return new HttpKafkaWithFetchMergeConfigBuilder<>(mapper);
    }
}
