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
package io.aklivity.zilla.runtime.binding.kafka.api;

import java.nio.ByteOrder;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32nFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.ListOffsetsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.PartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.TopicResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_offsets_v6.TopicResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) ListOffsets v6 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaListOffsetsResponse} view on top - a fixed-default-on-read behavior the flyweight
 * generator cannot produce, since generated builders only default missing fields on write.
 */
public final class KafkaListOffsetsResponseV6FW implements KafkaListOffsetsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW topicCountRO = new Varuint32nFW();
    private final TopicResponseFW topicResponseRO = new TopicResponseFW();
    private final PartitionResponseFW partitionResponseRO = new PartitionResponseFW();
    private final TopicResponsePart2FW topicResponsePart2RO = new TopicResponsePart2FW();
    private final ListOffsetsResponsePart2FW listOffsetsResponsePart2RO = new ListOffsetsResponsePart2FW();

    private final TopicView topicView = new TopicView();
    private final PartitionView partitionView = new PartitionView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int topicCount;
    private int topicsRemaining;
    private int partitionsRemaining;
    private boolean topicOpen;
    private boolean responseClosed;

    private int nameOffset;
    private int nameLength;
    private int topicPartitionCount;

    private int partitionIndex;
    private short errorCode;
    private long timestamp;
    private long endOffset;
    private int leaderEpoch;

    /**
     * Wraps a complete ListOffsets v6 response body: tagged fields, throttle time, and topic
     * count, followed by the topics themselves.
     */
    public KafkaListOffsetsResponseV6FW wrap(
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

        return wrapTopics(buffer, progress, limit, throttleTimeMillis, topicCount.value());
    }

    /**
     * Wraps just the topics of a ListOffsets v6 response body, for a caller that has already
     * decoded the throttle time and topic count itself.
     */
    public KafkaListOffsetsResponseV6FW wrapTopics(
        DirectBufferEx buffer,
        int offset,
        int limit,
        int throttleTimeMillis,
        int topicCount)
    {
        this.buffer = buffer;
        this.limit = limit;
        this.progress = offset;
        this.throttleTimeMillis = throttleTimeMillis;
        this.topicCount = topicCount;
        this.topicsRemaining = topicCount;
        this.partitionsRemaining = 0;
        this.topicOpen = false;
        this.responseClosed = false;

        return this;
    }

    /**
     * @return the offset just past the last byte consumed so far; final once {@link #hasNext()} returns false
     */
    public int limit()
    {
        return progress;
    }

    @Override
    public int throttleTimeMillis()
    {
        return throttleTimeMillis;
    }

    @Override
    public int topicCount()
    {
        return topicCount;
    }

    @Override
    public boolean hasNext()
    {
        // hasNext() consumes the per-topic and overall trailing tagged fields as the cursor moves
        // past the last partition or topic that reaches them, so next() never needs to look ahead.
        if (partitionsRemaining == 0 && topicOpen)
        {
            final TopicResponsePart2FW topicPart2 = topicResponsePart2RO.wrap(buffer, progress, limit);
            progress = topicPart2.limit();
            topicOpen = false;
        }

        if (topicsRemaining == 0 && partitionsRemaining == 0 && !responseClosed)
        {
            final ListOffsetsResponsePart2FW responsePart2 = listOffsetsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return topicsRemaining != 0 || partitionsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (partitionsRemaining != 0)
        {
            partitionsRemaining--;

            final PartitionResponseFW partition = partitionResponseRO.wrap(buffer, progress, limit);
            progress = partition.limit();

            this.partitionIndex = partition.partitionIndex();
            this.errorCode = partition.errorCode();
            this.timestamp = partition.timestamp();
            this.endOffset = partition.endOffset();
            this.leaderEpoch = partition.leaderEpoch();

            kind = Kind.PARTITION;
        }
        else
        {
            topicsRemaining--;

            final TopicResponseFW topic = topicResponseRO.wrap(buffer, progress, limit);
            progress = topic.limit();

            final VarStringFW name = topic.name();
            this.nameOffset = name.offset() + name.fieldSizeLength();
            this.nameLength = name.length();
            this.topicPartitionCount = topic.partitionCount();

            this.partitionsRemaining = topicPartitionCount;
            this.topicOpen = true;

            kind = Kind.TOPIC;
        }

        return kind;
    }

    @Override
    public Topic topic()
    {
        return topicView;
    }

    @Override
    public Partition partition()
    {
        return partitionView;
    }

    private final class TopicView implements Topic
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int nameOffset()
        {
            return nameOffset;
        }

        @Override
        public int nameLength()
        {
            return nameLength;
        }

        @Override
        public int partitionCount()
        {
            return topicPartitionCount;
        }
    }

    private final class PartitionView implements Partition
    {
        @Override
        public int partitionIndex()
        {
            return partitionIndex;
        }

        @Override
        public short errorCode()
        {
            return errorCode;
        }

        @Override
        public long timestamp()
        {
            return timestamp;
        }

        @Override
        public long endOffset()
        {
            return endOffset;
        }

        @Override
        public int leaderEpoch()
        {
            return leaderEpoch;
        }
    }
}
