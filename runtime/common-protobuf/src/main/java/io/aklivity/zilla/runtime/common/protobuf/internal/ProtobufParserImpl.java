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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * The pull cursor that decodes a message one {@link ProtobufEvent} at a time. It is the pipeline
 * primitive: a caller drives it directly with {@link #wrap}, {@link #hasNext} and
 * {@link #nextEvent(ProtobufParser.Mode)}, reading the current value through the {@link ProtobufSource}
 * accessors it implements. At a composite field {@link ProtobufParser.Mode#SEGMENTED} delivers it as raw
 * segment bytes rather than recursing; {@code Protobuf.stream} layers the push pipeline over the same
 * cursor, the pump selecting that mode when a stage requests it through its {@link ProtobufController}.
 * <p>
 * Schema-bound it emits {@code START_MESSAGE}/{@code END_MESSAGE}, {@code FIELD} then {@code VALUE} for
 * a scalar, and a nested {@code START_MESSAGE}…{@code END_MESSAGE} for a composite; schema-free it
 * tokenizes the wire into generic {@code FIELD}/{@code VALUE} pairs. Decode is a resumable state
 * machine over an explicit per-depth frame stack rather than the Java call stack, so each call yields
 * exactly one event.
 * <p>
 * Input may arrive whole ({@code last == true}, the default — every read bounded by the buffer) or as
 * successive windows ({@code last == false} then {@link #resume}). Each scope's end is tracked by the
 * swap-safe {@code end} position rather than the refillable byte {@code limit}, so a frame survives a
 * window swap; when a window is exhausted before the message completes, {@link #nextEvent} rewinds to the
 * last unit boundary, stashes the partial unit as carry, and returns {@code null} to signal starvation.
 */
public final class ProtobufParserImpl implements ProtobufParser, ProtobufSource
{
    private static final int MAX_DEPTH = 64;

    private static final int PHASE_NONE = 0;
    private static final int PHASE_SCALAR_VALUE = 1;
    private static final int PHASE_RAW_VALUE = 2;
    private static final int PHASE_COMPOSITE = 3;

    private final ProtobufSchema schema;
    private final String messageName;
    private final List<Frame> frames;
    private final ProtobufReader reader;

    private boolean started;
    private boolean done;
    private int depth;
    private int phase;

    private ProtobufField compositeField;
    private int regionOffset;
    private int regionLength;

    private ProtobufField field;
    private int fieldNumber;
    private ProtobufWireType wireType;
    private long longValue;
    private double doubleValue;
    private float floatValue;
    private DirectBuffer buffer;
    private int offset;
    private int length;
    private int deferred;

    public ProtobufParserImpl(
        ProtobufSchema schema,
        String messageName)
    {
        this.schema = schema;
        this.messageName = messageName;
        this.frames = new ArrayList<>();
        this.reader = new ProtobufReader();
    }

    @Override
    public ProtobufParser wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        reader.wrap(buffer, offset, length, last);
        this.started = false;
        this.done = false;
        this.depth = -1;
        this.phase = PHASE_NONE;
        this.deferred = 0;
        return this;
    }

    @Override
    public ProtobufParser resume(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last)
    {
        reader.resume(buffer, offset, length, last);
        return this;
    }

    @Override
    public boolean hasNext()
    {
        return !done;
    }

    @Override
    public ProtobufEvent nextEvent(
        Mode mode)
    {
        ProtobufEvent event;
        if (!started)
        {
            started = true;
            event = startRoot();
        }
        else
        {
            switch (phase)
            {
            case PHASE_SCALAR_VALUE:
                reader.mark();
                decodeScalar(field, reader);
                event = reader.starved() ? starve() : value();
                break;
            case PHASE_RAW_VALUE:
                reader.mark();
                decodeRaw(reader, fieldNumber, wireType);
                event = reader.starved() ? starve() : value();
                break;
            case PHASE_COMPOSITE:
                event = resolveComposite(mode);
                break;
            default:
                event = advance();
                break;
            }
        }
        return event;
    }

    @Override
    public ProtobufField field()
    {
        return field;
    }

    @Override
    public ProtobufMessage message()
    {
        return depth >= 0 ? frames.get(depth).message : null;
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

    @Override
    public int bytesDeferred()
    {
        return deferred;
    }

    private ProtobufEvent value()
    {
        phase = PHASE_NONE;
        return ProtobufEvent.VALUE;
    }

    private ProtobufEvent starve()
    {
        reader.rewind();
        reader.stash();
        return null;
    }

    private ProtobufEvent startRoot()
    {
        ProtobufMessage root = schema != null ? schema.message(messageName) : null;
        if (schema != null && root == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        depth = 0;
        Frame frame = frame(0);
        frame.message = root;
        frame.group = false;
        frame.groupNumber = -1;
        frame.packedEnd = -1L;
        frame.end = -1L;
        boundary();
        slice(reader.buffer(), reader.offset(), reader.available());
        return ProtobufEvent.START_MESSAGE;
    }

    private ProtobufEvent advance()
    {
        ProtobufEvent event = null;
        boolean starving = false;
        while (event == null && !starving)
        {
            Frame frame = frames.get(depth);
            if (frame.packedEnd >= 0L && reader.position() < frame.packedEnd)
            {
                field = frame.packedField;
                fieldNumber = field.number();
                wireType = field.type().wireType();
                phase = PHASE_SCALAR_VALUE;
                event = ProtobufEvent.FIELD;
            }
            else if (frame.packedEnd >= 0L)
            {
                frame.packedEnd = -1L;
            }
            else if (frame.end >= 0L && reader.position() >= frame.end)
            {
                event = endFrame();
            }
            else if (!reader.hasRemaining())
            {
                if (!reader.last())
                {
                    starving = true;
                }
                else if (depth == 0)
                {
                    event = endFrame();
                }
                else
                {
                    throw new ProtobufException(frame.group
                        ? "unterminated group " + frame.groupNumber
                        : "truncated message");
                }
            }
            else
            {
                reader.mark();
                event = readField(frame);
                if (reader.starved())
                {
                    reader.rewind();
                    reader.stash();
                    starving = true;
                }
            }
        }
        return event;
    }

    private ProtobufEvent readField(
        Frame frame)
    {
        ProtobufEvent event;
        ProtobufReader reader = this.reader;
        int tag = reader.readVarint32();
        if (reader.starved())
        {
            event = null;
        }
        else
        {
            int number = tag >>> 3;
            ProtobufWireType wt = ProtobufWireType.of(tag & 0x7);
            if (wt == ProtobufWireType.EGROUP)
            {
                if (!frame.group || number != frame.groupNumber)
                {
                    throw new ProtobufException("unexpected group end " + number);
                }
                event = endFrame();
            }
            else if (frame.message == null)
            {
                field = null;
                fieldNumber = number;
                wireType = wt;
                phase = PHASE_RAW_VALUE;
                event = ProtobufEvent.FIELD;
            }
            else
            {
                event = readSchemaField(frame, number, wt);
            }
        }
        return event;
    }

    private ProtobufEvent readSchemaField(
        Frame frame,
        int number,
        ProtobufWireType wt)
    {
        ProtobufEvent event;
        ProtobufReader reader = this.reader;
        ProtobufField fld = frame.message.field(number);
        if (fld == null)
        {
            reader.skipField(number, wt);
            event = null;
        }
        else if (fld.composite())
        {
            event = readComposite(fld, number, wt);
        }
        else if (fld.repeated() && fld.type().packable() && wt == ProtobufWireType.LEN)
        {
            int blockLength = reader.readLength();
            if (!reader.starved())
            {
                frame.packedEnd = reader.position() + blockLength;
                frame.packedField = fld;
            }
            event = null;
        }
        else
        {
            requireWireType(fld, wt);
            field = fld;
            fieldNumber = number;
            wireType = wt;
            phase = PHASE_SCALAR_VALUE;
            event = ProtobufEvent.FIELD;
        }
        return event;
    }

    private ProtobufEvent readComposite(
        ProtobufField fld,
        int number,
        ProtobufWireType wt)
    {
        ProtobufEvent event;
        ProtobufReader reader = this.reader;
        if (fld.type() == ProtobufType.GROUP)
        {
            if (wt != ProtobufWireType.SGROUP)
            {
                throw new ProtobufException("field " + number + " expected group");
            }
            regionOffset = reader.offset();
            regionLength = -1;
        }
        else
        {
            if (wt != ProtobufWireType.LEN)
            {
                throw new ProtobufException("field " + number + " expected length-delimited message");
            }
            regionLength = reader.readLength();
            regionOffset = reader.offset();
        }

        if (reader.starved())
        {
            event = null;
        }
        else
        {
            compositeField = fld;
            field = fld;
            fieldNumber = number;
            wireType = wt;
            phase = PHASE_COMPOSITE;
            event = ProtobufEvent.FIELD;
        }
        return event;
    }

    private ProtobufEvent resolveComposite(
        Mode mode)
    {
        ProtobufEvent event;
        if (mode == Mode.SEGMENTED)
        {
            event = segment();
        }
        else
        {
            depth++;
            if (depth >= MAX_DEPTH)
            {
                throw new ProtobufException("message nesting exceeds " + MAX_DEPTH);
            }
            ProtobufMessage nested = compositeField.message();
            if (nested == null)
            {
                throw new ProtobufException("unresolved message type " + compositeField.typeName());
            }
            boolean group = compositeField.type() == ProtobufType.GROUP;
            phase = PHASE_NONE;
            Frame frame = frame(depth);
            frame.message = nested;
            frame.group = group;
            frame.groupNumber = compositeField.number();
            frame.packedEnd = -1L;
            frame.end = group ? -1L : reader.position() + regionLength;
            boundary();
            slice(reader.buffer(), regionOffset, group ? 0 : regionLength);
            event = group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE;
        }
        return event;
    }

    private ProtobufEvent segment()
    {
        int length = regionLength;
        if (length < 0)
        {
            length = reader.skipGroup(compositeField.number()) - regionOffset;
        }
        else
        {
            reader.skip(length);
        }
        if (reader.starved())
        {
            // M1: a composite segment must be fully buffered; streaming segments arrive in M2
            throw new ProtobufException("segment exceeds available window");
        }
        phase = PHASE_NONE;
        field = compositeField;
        fieldNumber = compositeField.number();
        wireType = compositeField.type().wireType();
        slice(reader.buffer(), regionOffset, length);
        return ProtobufEvent.SEGMENT;
    }

    private ProtobufEvent endFrame()
    {
        boolean group = frames.get(depth).group;
        boundary();
        depth--;
        if (depth < 0)
        {
            done = true;
        }
        return group ? ProtobufEvent.END_GROUP : ProtobufEvent.END_MESSAGE;
    }

    private void decodeScalar(
        ProtobufField field,
        ProtobufReader reader)
    {
        switch (field.type())
        {
        case INT32:
            longValue = reader.readVarint32();
            break;
        case UINT32:
            longValue = reader.readVarint32() & 0xffffffffL;
            break;
        case SINT32:
            longValue = reader.readZigzag32();
            break;
        case INT64:
        case UINT64:
            longValue = reader.readVarint64();
            break;
        case SINT64:
            longValue = reader.readZigzag64();
            break;
        case FIXED32:
            longValue = reader.readFixed32() & 0xffffffffL;
            break;
        case SFIXED32:
            longValue = reader.readFixed32();
            break;
        case FIXED64:
        case SFIXED64:
            longValue = reader.readFixed64();
            break;
        case BOOL:
            longValue = reader.readVarint64() != 0L ? 1L : 0L;
            break;
        case ENUM:
            longValue = reader.readVarint32();
            break;
        case DOUBLE:
            doubleValue = Double.longBitsToDouble(reader.readFixed64());
            break;
        case FLOAT:
            floatValue = Float.intBitsToFloat(reader.readFixed32());
            break;
        case STRING:
        case BYTES:
            int len = reader.readLength();
            slice(reader.buffer(), reader.offset(), len);
            reader.skip(len);
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
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
            longValue = reader.readVarint64();
            slice(reader.buffer(), start, reader.offset() - start);
            break;
        case I64:
            longValue = reader.readFixed64();
            slice(reader.buffer(), start, 8);
            break;
        case I32:
            longValue = reader.readFixed32() & 0xffffffffL;
            slice(reader.buffer(), start, 4);
            break;
        case LEN:
            int len = reader.readLength();
            longValue = len;
            slice(reader.buffer(), reader.offset(), len);
            reader.skip(len);
            break;
        case SGROUP:
            int bodyStart = reader.offset();
            int bodyEnd = reader.skipGroup(number);
            longValue = 0;
            slice(reader.buffer(), bodyStart, bodyEnd - bodyStart);
            break;
        default:
            throw new ProtobufException("unexpected wire type " + wireType);
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

    private void boundary()
    {
        field = null;
        fieldNumber = -1;
        wireType = null;
    }

    private void slice(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    private Frame frame(
        int depth)
    {
        while (frames.size() <= depth)
        {
            frames.add(new Frame());
        }
        return frames.get(depth);
    }

    private static final class Frame
    {
        private ProtobufMessage message;
        private boolean group;
        private int groupNumber;
        private long end;
        private long packedEnd;
        private ProtobufField packedField;
    }
}
