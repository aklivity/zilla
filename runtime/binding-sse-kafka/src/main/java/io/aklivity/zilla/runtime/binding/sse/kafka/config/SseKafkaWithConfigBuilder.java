/*
 * Copyright 2021-2023 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class SseKafkaWithConfigBuilder<T> extends ConfigBuilder<T, SseKafkaWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private long compositeId = NO_COMPOSITE_ID;
    private String topic;
    private List<SseKafkaWithFilterConfig> filters;
    private String eventId;

    SseKafkaWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public SseKafkaWithConfigBuilder<T> compositeId(
        long compositeId)
    {
        this.compositeId = compositeId;
        return this;
    }

    public SseKafkaWithConfigBuilder<T> topic(
        String topic)
    {
        this.topic = topic;
        return this;
    }

    public SseKafkaWithFilterConfigBuilder<SseKafkaWithConfigBuilder<T>> filter()
    {
        return new SseKafkaWithFilterConfigBuilder<>(this::filter);
    }

    public SseKafkaWithConfigBuilder<T> filters(
        List<SseKafkaWithFilterConfig> filters)
    {
        this.filters = filters;
        return this;
    }

    public SseKafkaWithConfigBuilder<T> eventId(
        String eventId)
    {
        this.eventId = eventId;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SseKafkaWithConfigBuilder<T>> thisType()
    {
        return (Class<SseKafkaWithConfigBuilder<T>>) getClass();
    }

    @Override
    public T build()
    {
        return mapper.apply(new SseKafkaWithConfig(compositeId, topic, filters, eventId));
    }

    private SseKafkaWithConfigBuilder<T> filter(
        SseKafkaWithFilterConfig filter)
    {
        if (filters == null)
        {
            filters = new LinkedList<>();
        }

        filters.add(filter);
        return this;
    }
}
