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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import java.util.List;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String16FW;

public class SseKafkaWithResult
{
    private final String16FW topic;
    private final Array32FW<KafkaOffsetFW> partitions;
    private final List<SseKafkaWithFilterResult> filters;
    private final String eventId;

    SseKafkaWithResult(
        String16FW topic,
        Array32FW<KafkaOffsetFW> partitions,
        List<SseKafkaWithFilterResult> filters,
        String eventId)
    {
        this.topic = topic;
        this.partitions = partitions;
        this.filters = filters;
        this.eventId = eventId;
    }

    public String16FW topic()
    {
        return topic;
    }

    public Array32FW<KafkaOffsetFW> partitions()
    {
        return partitions;
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }
    }

    public String eventId()
    {
        return eventId;
    }
}
