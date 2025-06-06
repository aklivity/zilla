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

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;

public final class HttpKafkaIdempotencyConfig
{
    public final String8FW header;

    public HttpKafkaIdempotencyConfig(
        String8FW header)
    {
        this.header = header;
    }

    public static HttpKafkaIdempotencyConfigBuilder<HttpKafkaIdempotencyConfig> builder()
    {
        return new HttpKafkaIdempotencyConfigBuilder<>(HttpKafkaIdempotencyConfig.class::cast);
    }

    public static <T> HttpKafkaIdempotencyConfigBuilder<T> builder(
        Function<HttpKafkaIdempotencyConfig, T> mapper)
    {
        return new HttpKafkaIdempotencyConfigBuilder<>(mapper);
    }
}
