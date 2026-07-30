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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32nFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_topics.DeleteTopicsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_topics.TopicResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DeleteTopics v6 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link DeleteTopicsResponse} view on top - a fixed-default-on-read behavior the flyweight
 * generator cannot produce, since generated builders only default missing fields on write.
 */
public final class DeleteTopicsResponseV6FW implements DeleteTopicsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW topicCountRO = new Varuint32nFW();
    private final TopicResponseFW topicResponseRO = new TopicResponseFW();
    private final DeleteTopicsResponsePart2FW deleteTopicsResponsePart2RO = new DeleteTopicsResponsePart2FW();

    private final TopicView topicView = new TopicView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int topicCount;
    private int topicsRemaining;
    private boolean responseClosed;

    private int topicNameOffset;
    private int topicNameLength;
    private long topicIdMostSigBits;
    private long topicIdLeastSigBits;
    private short topicError;
    private int topicMessageOffset;
    private int topicMessageLength;

    /**
     * Wraps a complete DeleteTopics v6 response body: tagged fields, throttle time, and topic count,
     * followed by the topics themselves.
     */
    public DeleteTopicsResponseV6FW wrap(
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
     * Wraps just the topics of a DeleteTopics v6 response body, for a caller that has already decoded
     * the throttle time and topic count itself (e.g. via a generated header flyweight covering a wider
     * response envelope, such as one that also carries a correlation id).
     */
    public DeleteTopicsResponseV6FW wrapTopics(
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
        if (topicsRemaining == 0 && !responseClosed)
        {
            final DeleteTopicsResponsePart2FW responsePart2 = deleteTopicsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return topicsRemaining != 0;
    }

    @Override
    public Topic next()
    {
        topicsRemaining--;

        final TopicResponseFW topic = topicResponseRO.wrap(buffer, progress, limit);
        progress = topic.limit();

        final VarStringFW name = topic.name();
        final OctetsFW topicId = topic.topicId();
        final VarStringFW message = topic.message();

        this.topicNameOffset = name.offset() + name.fieldSizeLength();
        this.topicNameLength = name.length();
        this.topicIdMostSigBits = topicId.buffer().getLong(topicId.offset(), ByteOrder.BIG_ENDIAN);
        this.topicIdLeastSigBits = topicId.buffer().getLong(topicId.offset() + Long.BYTES, ByteOrder.BIG_ENDIAN);
        this.topicError = topic.error();
        this.topicMessageOffset = message.offset() + message.fieldSizeLength();
        this.topicMessageLength = message.length();

        return topicView;
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
            return topicNameOffset;
        }

        @Override
        public int nameLength()
        {
            return topicNameLength;
        }

        @Override
        public long topicIdMostSigBits()
        {
            return topicIdMostSigBits;
        }

        @Override
        public long topicIdLeastSigBits()
        {
            return topicIdLeastSigBits;
        }

        @Override
        public short error()
        {
            return topicError;
        }

        @Override
        public int messageOffset()
        {
            return topicMessageOffset;
        }

        @Override
        public int messageLength()
        {
            return topicMessageLength;
        }
    }
}
