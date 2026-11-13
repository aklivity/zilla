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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.OffsetDeleteResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.PartitionResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.offset_delete.TopicResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) OffsetDelete v0 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaOffsetDeleteResponse} view on top. Version 0 is not a flexible version, so there are
 * no tagged fields to consume.
 */
public final class KafkaOffsetDeleteResponseV0FW implements KafkaOffsetDeleteResponse
{
    private final OffsetDeleteResponseFW offsetDeleteResponseRO = new OffsetDeleteResponseFW();
    private final TopicResponseFW topicResponseRO = new TopicResponseFW();
    private final PartitionResponseFW partitionResponseRO = new PartitionResponseFW();

    private final TopicView topicView = new TopicView();
    private final PartitionView partitionView = new PartitionView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private short error;
    private int throttleTimeMillis;
    private int topicCount;
    private int topicsRemaining;
    private int partitionsRemaining;

    private int nameOffset;
    private int nameLength;
    private int topicPartitionCount;

    private int partitionIndex;
    private short errorCode;

    /**
     * Wraps a complete OffsetDelete v0 response body: top-level error, throttle time and topic
     * count, followed by the topics themselves.
     */
    public KafkaOffsetDeleteResponseV0FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final OffsetDeleteResponseFW response = offsetDeleteResponseRO.wrap(buffer, offset, limit);

        this.buffer = buffer;
        this.limit = limit;
        this.progress = response.limit();
        this.error = response.error();
        this.throttleTimeMillis = response.throttleTimeMillis();
        this.topicCount = response.topicCount();
        this.topicsRemaining = topicCount;
        this.partitionsRemaining = 0;

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
    public short error()
    {
        return error;
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
        return topicsRemaining != 0 || partitionsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        if (topicsRemaining < 0 || partitionsRemaining < 0)
        {
            throw new IllegalStateException("negative array count in OffsetDelete response");
        }

        final Kind kind;

        if (partitionsRemaining != 0)
        {
            partitionsRemaining--;

            final PartitionResponseFW partition = partitionResponseRO.wrap(buffer, progress, limit);
            progress = partition.limit();

            this.partitionIndex = partition.partitionIndex();
            this.errorCode = partition.error();

            kind = Kind.PARTITION;
        }
        else
        {
            topicsRemaining--;

            final TopicResponseFW topic = topicResponseRO.wrap(buffer, progress, limit);
            progress = topic.limit();

            final String16FW name = topic.name();
            this.nameOffset = name.offset() + name.fieldSizeLength();
            this.nameLength = name.length();
            this.topicPartitionCount = topic.partitionCount();

            this.partitionsRemaining = topicPartitionCount;

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
    }
}
