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

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * The runnable pipeline: a descriptor-driven driver that decodes a fully-buffered message and pumps
 * structured (or, on demand, segmented) events through the stage chain to the terminal sink. It is
 * also the {@link ProtobufController} for the chain — a stage's {@link #segmentable()} arms the next
 * composite field for verbatim segment delivery.
 */
public final class ProtobufPipelineImpl implements ProtobufPipeline, ProtobufController
{
    private static final int MAX_DEPTH = 64;

    private final ProtobufSchema schema;
    private final String messageName;
    private final ProtobufSink head;
    private final SourceView source;
    private final List<ProtobufReader> readers;

    private boolean armed;

    public ProtobufPipelineImpl(
        ProtobufSchema schema,
        String messageName,
        List<ProtobufTransform> transforms,
        ProtobufSink sink)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.source = new SourceView();
        this.readers = new ArrayList<>();

        ProtobufSink chain = sink;
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            chain = new StageSink(transforms.get(i), chain);
        }
        this.head = chain;
    }

    @Override
    public void reset()
    {
        head.reset();
        armed = false;
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Status status;
        try
        {
            if (schema == null)
            {
                status = emitRaw(buffer, offset, length);
            }
            else
            {
                ProtobufMessage message = schema.message(messageName);
                status = message == null ? Status.REJECTED : emitMessage(message, buffer, offset, length, 0);
            }
        }
        catch (ProtobufException ex)
        {
            status = Status.REJECTED;
        }
        return status;
    }

    private Status emitRaw(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        Status status = emitRawEvent(ProtobufEvent.START_MESSAGE, -1, null);
        ProtobufReader reader = reader(0).wrap(buffer, offset, length);
        while (status != Status.REJECTED && reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            status = emitRawEvent(ProtobufEvent.FIELD, number, wireType);
            if (status != Status.REJECTED)
            {
                decodeRaw(reader, number, wireType);
                status = emitRawEvent(ProtobufEvent.VALUE, number, wireType);
            }
        }
        if (status != Status.REJECTED)
        {
            status = emitRawEvent(ProtobufEvent.END_MESSAGE, -1, null);
        }
        return status;
    }

    private Status emitRawEvent(
        ProtobufEvent event,
        int number,
        ProtobufWireType wireType)
    {
        source.field = null;
        source.fieldNumber = number;
        source.wireType = wireType;
        return head.feed(this, source, event);
    }

    private void decodeRaw(
        ProtobufReader reader,
        int number,
        ProtobufWireType wireType)
    {
        int start = reader.offset();
        switch (wireType)
        {
        case VARINT:
            source.longValue = reader.readVarint64();
            slice(reader.buffer(), start, reader.offset() - start);
            break;
        case I64:
            source.longValue = reader.readFixed64();
            slice(reader.buffer(), start, 8);
            break;
        case I32:
            source.longValue = reader.readFixed32() & 0xffffffffL;
            slice(reader.buffer(), start, 4);
            break;
        case LEN:
            int len = reader.readLength();
            source.longValue = len;
            slice(reader.buffer(), reader.offset(), len);
            reader.skip(len);
            break;
        case SGROUP:
            int bodyStart = reader.offset();
            int bodyEnd = reader.skipGroup(number);
            source.longValue = 0;
            slice(reader.buffer(), bodyStart, bodyEnd - bodyStart);
            break;
        default:
            throw new ProtobufException("unexpected wire type " + wireType);
        }
    }

    private void slice(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        source.buffer = buffer;
        source.offset = offset;
        source.length = length;
    }

    private ProtobufReader reader(
        int depth)
    {
        while (readers.size() <= depth)
        {
            readers.add(new ProtobufReader());
        }
        return readers.get(depth);
    }

    @Override
    public void segmentable()
    {
        armed = true;
    }

    private Status emitMessage(
        ProtobufMessage message,
        DirectBuffer buffer,
        int offset,
        int length,
        int depth)
    {
        if (depth >= MAX_DEPTH)
        {
            throw new ProtobufException("message nesting exceeds " + MAX_DEPTH);
        }

        Status status = deliver(ProtobufEvent.START_MESSAGE, null, buffer, offset, length);
        ProtobufReader reader = reader(depth).wrap(buffer, offset, length);
        while (status != Status.REJECTED && reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            ProtobufField field = message.field(number);
            if (field == null)
            {
                reader.skipField(number, wireType);
            }
            else if (field.composite())
            {
                status = emitComposite(field, reader, number, wireType, buffer, depth);
            }
            else if (field.repeated() && field.type().packable() && wireType == ProtobufWireType.LEN)
            {
                status = emitPacked(field, reader);
            }
            else
            {
                requireWireType(field, wireType);
                status = deliver(ProtobufEvent.FIELD, field, buffer, 0, 0);
                if (status != Status.REJECTED)
                {
                    decodeScalar(field, reader);
                    status = deliver(ProtobufEvent.VALUE, field, buffer, 0, 0);
                }
            }
        }

        if (status != Status.REJECTED)
        {
            status = deliver(ProtobufEvent.END_MESSAGE, null, buffer, 0, 0);
        }
        return status;
    }

    private Status emitComposite(
        ProtobufField field,
        ProtobufReader reader,
        int number,
        ProtobufWireType wireType,
        DirectBuffer buffer,
        int depth)
    {
        int regionOffset;
        int regionLength;
        if (field.type() == ProtobufType.GROUP)
        {
            if (wireType != ProtobufWireType.SGROUP)
            {
                throw new ProtobufException("field " + number + " expected group");
            }
            regionOffset = reader.offset();
            regionLength = reader.skipGroup(number) - regionOffset;
        }
        else
        {
            if (wireType != ProtobufWireType.LEN)
            {
                throw new ProtobufException("field " + number + " expected length-delimited message");
            }
            regionLength = reader.readLength();
            regionOffset = reader.offset();
            reader.skip(regionLength);
        }

        armed = false;
        Status status = deliver(ProtobufEvent.FIELD, field, buffer, 0, 0);
        if (status != Status.REJECTED)
        {
            if (armed)
            {
                status = deliver(ProtobufEvent.START_SEGMENT, field, buffer, regionOffset, regionLength);
                if (status != Status.REJECTED)
                {
                    status = deliver(ProtobufEvent.END_SEGMENT, field, buffer, regionOffset, regionLength);
                }
            }
            else
            {
                ProtobufMessage nested = schema.resolveMessage(field);
                status = emitMessage(nested, buffer, regionOffset, regionLength, depth + 1);
            }
        }
        return status;
    }

    private Status emitPacked(
        ProtobufField field,
        ProtobufReader reader)
    {
        int blockLength = reader.readLength();
        int blockLimit = reader.offset() + blockLength;
        DirectBuffer buffer = reader.buffer();
        Status status = Status.PENDING;
        while (status != Status.REJECTED && reader.offset() < blockLimit)
        {
            status = deliver(ProtobufEvent.FIELD, field, buffer, 0, 0);
            if (status != Status.REJECTED)
            {
                decodeScalar(field, reader);
                status = deliver(ProtobufEvent.VALUE, field, buffer, 0, 0);
            }
        }
        return status;
    }

    private Status deliver(
        ProtobufEvent event,
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        source.field = field;
        source.fieldNumber = field != null ? field.number() : -1;
        source.wireType = field != null ? field.type().wireType() : null;
        if (event.segmented() || event == ProtobufEvent.START_MESSAGE)
        {
            source.buffer = buffer;
            source.offset = offset;
            source.length = length;
        }
        return head.feed(this, source, event);
    }

    private void decodeScalar(
        ProtobufField field,
        ProtobufReader reader)
    {
        switch (field.type())
        {
        case INT32:
            source.longValue = reader.readVarint32();
            break;
        case UINT32:
            source.longValue = reader.readVarint32() & 0xffffffffL;
            break;
        case SINT32:
            source.longValue = reader.readZigzag32();
            break;
        case INT64:
        case UINT64:
            source.longValue = reader.readVarint64();
            break;
        case SINT64:
            source.longValue = reader.readZigzag64();
            break;
        case FIXED32:
            source.longValue = reader.readFixed32() & 0xffffffffL;
            break;
        case SFIXED32:
            source.longValue = reader.readFixed32();
            break;
        case FIXED64:
        case SFIXED64:
            source.longValue = reader.readFixed64();
            break;
        case BOOL:
            source.longValue = reader.readVarint64() != 0L ? 1L : 0L;
            break;
        case ENUM:
            source.longValue = reader.readVarint32();
            break;
        case DOUBLE:
            source.doubleValue = Double.longBitsToDouble(reader.readFixed64());
            break;
        case FLOAT:
            source.floatValue = Float.intBitsToFloat(reader.readFixed32());
            break;
        case STRING:
        case BYTES:
            int len = reader.readLength();
            source.buffer = reader.buffer();
            source.offset = reader.offset();
            source.length = len;
            reader.skip(len);
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private void requireWireType(
        ProtobufField field,
        ProtobufWireType wireType)
    {
        boolean packed = field.repeated() && field.type().packable() && wireType == ProtobufWireType.LEN;
        if (!packed && wireType != field.type().wireType())
        {
            throw new ProtobufException("field " + field.number() + " wire type " + wireType +
                " incompatible with " + field.type());
        }
    }

    private final class StageSink implements ProtobufSink
    {
        private final ProtobufTransform transform;
        private final ProtobufSink downstream;

        private StageSink(
            ProtobufTransform transform,
            ProtobufSink downstream)
        {
            this.transform = transform;
            this.downstream = downstream;
        }

        @Override
        public Status feed(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            return transform.feed(control, source, event, downstream);
        }

        @Override
        public void reset()
        {
            transform.reset();
            downstream.reset();
        }
    }

    private static final class SourceView implements ProtobufSource
    {
        private ProtobufField field;
        private int fieldNumber;
        private ProtobufWireType wireType;
        private long longValue;
        private double doubleValue;
        private float floatValue;
        private DirectBuffer buffer;
        private int offset;
        private int length;

        @Override
        public ProtobufField field()
        {
            return field;
        }

        @Override
        public int fieldNumber()
        {
            return fieldNumber;
        }

        @Override
        public ProtobufWireType wireType()
        {
            return wireType;
        }

        @Override
        public long longValue()
        {
            return longValue;
        }

        @Override
        public double doubleValue()
        {
            return doubleValue;
        }

        @Override
        public float floatValue()
        {
            return floatValue;
        }

        @Override
        public DirectBuffer buffer()
        {
            return buffer;
        }

        @Override
        public int offset()
        {
            return offset;
        }

        @Override
        public int length()
        {
            return length;
        }
    }
}
