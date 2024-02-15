/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.spy;

import static org.agrona.BitUtil.align;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.ALIGNMENT;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.HEADER_LENGTH;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.lengthOffset;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.typeOffset;
import static org.agrona.concurrent.ringbuffer.RingBuffer.PADDING_MSG_TYPE_ID;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.checkCapacity;

import org.agrona.concurrent.AtomicBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class OneToOneRingBufferSpy implements RingBufferSpy
{
    private final int capacity;
    private final AtomicBuffer buffer;

    private int spyPosition;

    public OneToOneRingBufferSpy(
        final AtomicBuffer buffer)
    {
        this.buffer = buffer;
        checkCapacity(buffer.capacity());
        capacity = buffer.capacity() - TRAILER_LENGTH;
        buffer.verifyAlignment();
        spyPosition = 0;
    }

    @Override
    public int spy(
        final MessageConsumer handler)
    {
        return spy(handler, Integer.MAX_VALUE);
    }

    @Override
    public int spy(
        final MessageConsumer handler,
        final int messageCountLimit)
    {
        int messagesRead = 0;
        int bytesRead = 0;

        final int capacity = this.capacity;
        final int headIndex = spyPosition & (capacity - 1);
        final int contiguousBlockLength = capacity - headIndex;

        try
        {
            while (bytesRead < contiguousBlockLength && messagesRead < messageCountLimit)
            {
                final int recordIndex = headIndex + bytesRead;
                final int recordLength = buffer.getIntVolatile(lengthOffset(recordIndex));
                if (recordLength <= 0)
                {
                    break;
                }

                bytesRead += align(recordLength, ALIGNMENT);

                final int messageTypeId = buffer.getInt(typeOffset(recordIndex));
                if (PADDING_MSG_TYPE_ID == messageTypeId)
                {
                    continue;
                }

                ++messagesRead;
                handler.accept(messageTypeId, buffer, recordIndex + HEADER_LENGTH, recordLength - HEADER_LENGTH);
            }
        }
        finally
        {
            if (bytesRead != 0)
            {
                spyPosition += bytesRead;
            }
        }

        return messagesRead;
    }
}
