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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.WithConfig;

public final class SseKafkaWithConfig extends WithConfig
{
    public static final String EVENT_ID_ETAG_ONLY = "${etag}";
    public static final String EVENT_ID_KEY64_AND_ETAG = "[\"${base64(key)}\",\"${etag}\"]";
    public static final String EVENT_ID_DEFAULT = EVENT_ID_ETAG_ONLY;

    public final String topic;
    public final Optional<List<SseKafkaWithFilterConfig>> filters;
    public final String eventId;

    SseKafkaWithConfig(
        long compositeId,
        String topic,
        List<SseKafkaWithFilterConfig> filters,
        String eventId)
    {
        super(compositeId);
        this.topic = topic;
        this.filters = Optional.ofNullable(filters);
        this.eventId = Objects.requireNonNull(eventId);
    }

    public static SseKafkaWithConfigBuilder<SseKafkaWithConfig> builder()
    {
        return new SseKafkaWithConfigBuilder<>(SseKafkaWithConfig.class::cast);
    }

    public static <T> SseKafkaWithConfigBuilder<T> builder(
        Function<WithConfig, T> mapper)
    {
        return new SseKafkaWithConfigBuilder<>(mapper);
    }
}
