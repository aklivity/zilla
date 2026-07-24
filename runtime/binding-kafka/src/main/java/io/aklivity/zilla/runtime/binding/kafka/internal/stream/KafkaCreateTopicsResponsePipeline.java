/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32nFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.ConfigResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.CreateTopicsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.TopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_topics.TopicResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public final class KafkaCreateTopicsResponsePipeline
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW topicCountRO = new Varuint32nFW();
    private final TopicResponseFW topicResponseRO = new TopicResponseFW();
    private final ConfigResponseFW configResponseRO = new ConfigResponseFW();
    private final TopicResponsePart2FW topicResponsePart2RO = new TopicResponsePart2FW();
    private final CreateTopicsResponsePart2FW createTopicsResponsePart2RO = new CreateTopicsResponsePart2FW();

    public Response decode(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW topicCount = topicCountRO.wrap(buffer, progress, limit);
        progress = topicCount.limit();

        final List<TopicResult> topics = new ArrayList<>();
        for (int topicIndex = 0; topicIndex < topicCount.value(); topicIndex++)
        {
            final TopicResponseFW topic = topicResponseRO.wrap(buffer, progress, limit);
            progress = topic.limit();

            final OctetsFW topicId = topic.topicId();
            final long topicIdMostSigBits = topicId.buffer().getLong(topicId.offset(), ByteOrder.BIG_ENDIAN);
            final long topicIdLeastSigBits = topicId.buffer().getLong(topicId.offset() + Long.BYTES, ByteOrder.BIG_ENDIAN);

            final List<ConfigResult> configs = new ArrayList<>();
            for (int configIndex = 0; configIndex < topic.configCount(); configIndex++)
            {
                final ConfigResponseFW config = configResponseRO.wrap(buffer, progress, limit);
                progress = config.limit();

                configs.add(new ConfigResult(
                    config.name().asString(),
                    config.value().asString(),
                    config.readOnly() != 0,
                    config.configSource(),
                    config.isSensitive() != 0));
            }

            final TopicResponsePart2FW topicPart2 = topicResponsePart2RO.wrap(buffer, progress, limit);
            progress = topicPart2.limit();

            topics.add(new TopicResult(
                topic.name().asString(),
                topicIdMostSigBits,
                topicIdLeastSigBits,
                topic.error(),
                topic.message().asString(),
                topic.numPartitions(),
                topic.replicationFactor(),
                configs));
        }

        final CreateTopicsResponsePart2FW responsePart2 = createTopicsResponsePart2RO.wrap(buffer, progress, limit);
        progress = responsePart2.limit();

        assert progress <= limit;

        return new Response(throttleTimeMillis, topics);
    }

    public record Response(
        int throttleTimeMillis,
        List<TopicResult> topics)
    {
    }

    public record TopicResult(
        String name,
        long topicIdMostSigBits,
        long topicIdLeastSigBits,
        short error,
        String message,
        int numPartitions,
        short replicationFactor,
        List<ConfigResult> configs)
    {
    }

    public record ConfigResult(
        String name,
        String value,
        boolean readOnly,
        byte configSource,
        boolean isSensitive)
    {
    }
}
