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
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpKafkaIdempotencyConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaIdempotencyConfigBuilder<T>>
{
    private static final String8FW IDEMPOTENCY_HEADER_DEFAULT = new String8FW("idempotency-key");

    private final Function<HttpKafkaIdempotencyConfig, T> mapper;

    private String header;

    HttpKafkaIdempotencyConfigBuilder(
        Function<HttpKafkaIdempotencyConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaIdempotencyConfigBuilder<T> header(
        String header)
    {
        this.header = header;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaIdempotencyConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaIdempotencyConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        String8FW header = this.header != null ?  new String8FW(this.header) : IDEMPOTENCY_HEADER_DEFAULT;
        return mapper.apply(new HttpKafkaIdempotencyConfig(header));
    }
}
