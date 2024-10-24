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
package io.aklivity.zilla.runtime.binding.sse.kafka.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class SseKafkaWithFilterConfigBuilder<T> extends ConfigBuilder<T, SseKafkaWithFilterConfigBuilder<T>>
{
    private final Function<SseKafkaWithFilterConfig, T> mapper;

    private String key;
    private List<SseKafkaWithFilterHeaderConfig> headers;

    SseKafkaWithFilterConfigBuilder(
        Function<SseKafkaWithFilterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public SseKafkaWithFilterConfigBuilder<T> key(
        String key)
    {
        this.key = key;
        return this;
    }

    public SseKafkaWithFilterHeaderConfigBuilder<SseKafkaWithFilterConfigBuilder<T>> header()
    {
        return new SseKafkaWithFilterHeaderConfigBuilder<>(this::header);
    }

    public SseKafkaWithFilterConfigBuilder<T> headers(
        List<SseKafkaWithFilterHeaderConfig> headers)
    {
        this.headers = headers;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseKafkaWithFilterConfigBuilder<T>> thisType()
    {
        return (Class<SseKafkaWithFilterConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new SseKafkaWithFilterConfig(key, headers));
    }

    private SseKafkaWithFilterConfigBuilder<T> header(
        SseKafkaWithFilterHeaderConfig header)
    {
        if (headers == null)
        {
            headers = new LinkedList<>();
        }

        headers.add(header);
        return this;
    }
}
