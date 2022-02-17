/*
 * Copyright 2021-2022 Aklivity Inc
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

import static java.util.Collections.emptyList;

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream.SseKafkaIdHelper;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseBeginExFW;

public final class SseKafkaWithResolver
{
    private static final List<SseKafkaWithFilterConfig> EMPTY_FILTERS = emptyList();

    private final SseKafkaWithConfig with;

    public SseKafkaWithResolver(
        SseKafkaWithConfig with)
    {
        this.with = with;
    }

    public SseKafkaWithResult resolve(
        SseBeginExFW sseBeginEx,
        SseKafkaIdHelper sseEventId)
    {
        final String8FW lastEventId = sseBeginEx != null ? sseBeginEx.lastEventId() : null;
        final Array32FW<KafkaOffsetFW> partitions = sseEventId.decode(lastEventId);

        // TODO: hoist to resolver constructor if constant
        String16FW topic = new String16FW(with.topic);
        List<SseKafkaWithFilterConfig> filters = with.filters.orElse(EMPTY_FILTERS);

        // TODO: resolve parameters from sseBeginEx path
        return new SseKafkaWithResult(topic, partitions, filters);
    }

    public static class SseKafkaWithResult
    {
        private final String16FW topic;
        private final Array32FW<KafkaOffsetFW> partitions;
        private final List<SseKafkaWithFilterConfig> filters;

        SseKafkaWithResult(
            String16FW topic,
            Array32FW<KafkaOffsetFW> partitions,
            List<SseKafkaWithFilterConfig> filters)
        {
            this.topic = topic;
            this.partitions = partitions;
            this.filters = filters;
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
            Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> fs)
        {
            for (SseKafkaWithFilterConfig filter : filters)
            {
                fs.item(i ->
                {
                    if (filter.key.isPresent())
                    {
                        DirectBuffer key = new String16FW(filter.key.get()).value();
                        i.conditionsItem(c -> c.key(k -> k.length(key.capacity())
                                                          .value(key, 0, key.capacity())));
                    }

                    if (filter.headers.isPresent())
                    {
                        for (SseKafkaWithFilterHeaderConfig header : filter.headers.get())
                        {
                            DirectBuffer name = new String16FW(header.name).value();
                            DirectBuffer value = new String16FW(header.value).value();
                            i.conditionsItem(c -> c.header(h -> h.nameLen(name.capacity())
                                                                 .name(name, 0, name.capacity())
                                                                 .valueLen(value.capacity())
                                                                 .value(value, 0, value.capacity())));
                        }
                    }
                });
            }
        }
    }
}
