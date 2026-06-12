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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A terminal sink for the schema-free pipeline: writes each generic event back out as wire by its
 * {@link ProtobufSource#fieldNumber()} and {@link ProtobufSource#wireType()}, splicing the raw value
 * slice verbatim. Composed with the schema-free parser it is a lossless structural copy; a transform
 * between them can keep/drop/redact fields by number with no schema.
 * <p>
 * A length-delimited value may arrive in pieces (chunked input, {@link ProtobufSource#deferredBytes()}
 * {@code > 0}) and may exceed the bounded output, so it streams through {@link ProtobufGenerator#writeSegment}
 * — its total-length prefix written once, its body across chunks — mirroring the typed sink.
 */
public final class ProtobufUntypedSinkImpl implements ProtobufSink
{
    private final ProtobufGenerator generator;

    private int depth;
    private int valueWritten;
    private int valueTotal;
    private ProtobufEvent suspended;

    public ProtobufUntypedSinkImpl(
        ProtobufGenerator generator)
    {
        this.generator = generator;
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = dispatch(source, event);
        if (status == ProtobufPipeline.Status.SUSPENDED)
        {
            suspended = event;
        }
        return status;
    }

    @Override
    public ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source)
    {
        return dispatch(source, suspended);
    }

    @Override
    public void reset()
    {
        depth = 0;
        valueWritten = 0;
        valueTotal = 0;
        suspended = null;
    }

    private ProtobufPipeline.Status dispatch(
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        switch (event)
        {
        case START_MESSAGE:
            depth++;
            break;
        case END_MESSAGE:
            depth--;
            if (depth == 0)
            {
                status = ProtobufPipeline.Status.COMPLETED;
            }
            break;
        case VALUE:
            status = onValue(source);
            break;
        default:
            break;
        }
        return status;
    }

    private ProtobufPipeline.Status onValue(
        ProtobufSource source)
    {
        ProtobufPipeline.Status status;
        if (source.wireType() == ProtobufWireType.LEN)
        {
            status = writeChunk(source);
        }
        else
        {
            DirectBuffer segment = source.segment();
            generator.writeValue(source.fieldNumber(), source.wireType(), segment, 0, segment.capacity());
            status = ProtobufPipeline.Status.ADVANCED;
        }
        return status;
    }

    private ProtobufPipeline.Status writeChunk(
        ProtobufSource source)
    {
        int number = source.fieldNumber();
        DirectBuffer segment = source.segment();
        int length = segment.capacity();
        int deferred = source.deferredBytes();
        int remaining = generator.remaining();
        if (valueWritten == 0)
        {
            // the first chunk carries the whole value's length: length now plus all that is still deferred
            valueTotal = length + deferred;
        }
        int chunkRemaining = valueTotal - deferred - valueWritten;
        int segmentOffset = length - chunkRemaining;
        int header = valueWritten == 0 ? tagSize(number) + varintSize(valueTotal) : 0;
        ProtobufPipeline.Status status;
        if (valueWritten == 0 && header + 1 > remaining && generator.length() > 0)
        {
            generator.flush();
            status = ProtobufPipeline.Status.SUSPENDED;
        }
        else if (valueWritten == 0 && header + 1 > remaining)
        {
            throw new ProtobufException("value header exceeds output limit");
        }
        else
        {
            int now = Math.min(remaining - header, chunkRemaining);
            generator.writeSegment(number, segment, segmentOffset, now, valueTotal - valueWritten - now);
            valueWritten += now;
            if (now < chunkRemaining)
            {
                generator.flush();
                status = ProtobufPipeline.Status.SUSPENDED;
            }
            else if (deferred > 0)
            {
                status = ProtobufPipeline.Status.ADVANCED;
            }
            else
            {
                valueWritten = 0;
                valueTotal = 0;
                status = ProtobufPipeline.Status.ADVANCED;
            }
        }
        return status;
    }

    private static int tagSize(
        int number)
    {
        return varintSize((long) number << 3);
    }

    private static int varintSize(
        long value)
    {
        long remaining = value & 0xffffffffL;
        int size = 1;
        while (remaining >= 0x80L)
        {
            remaining >>>= 7;
            size++;
        }
        return size;
    }
}
