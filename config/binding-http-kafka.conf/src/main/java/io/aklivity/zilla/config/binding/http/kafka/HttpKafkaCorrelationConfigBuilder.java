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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class HttpKafkaCorrelationConfigBuilder<T> extends ConfigBuilder<T, HttpKafkaCorrelationConfigBuilder<T>>
{
    private static final String CORRELATION_HEADERS_REPLY_TO_DEFAULT = "zilla:reply-to";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = "zilla:correlation-id";

    private final Function<HttpKafkaCorrelationConfig, T> mapper;

    private String replyTo;
    private String correlationId;

    HttpKafkaCorrelationConfigBuilder(
        Function<HttpKafkaCorrelationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public HttpKafkaCorrelationConfigBuilder<T> replyTo(
        String replyTo)
    {
        this.replyTo = replyTo;
        return this;
    }

    public HttpKafkaCorrelationConfigBuilder<T> correlationId(
        String correlationId)
    {
        this.correlationId = correlationId;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpKafkaCorrelationConfigBuilder<T>> thisType()
    {
        return (Class<HttpKafkaCorrelationConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        String replyTo = this.replyTo != null
            ? this.replyTo
            : CORRELATION_HEADERS_REPLY_TO_DEFAULT;
        String correlationId = this.correlationId != null
            ? this.correlationId
            : CORRELATION_HEADERS_CORRELATION_ID_DEFAULT;
        return mapper.apply(new HttpKafkaCorrelationConfig(replyTo, correlationId));
    }
}
