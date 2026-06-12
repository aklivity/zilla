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
 * The pull cursor that decodes a fully-buffered message one {@link ProtobufEvent} at a time. It is the
 * pipeline primitive: a caller drives it directly with {@link #wrap}, {@link #hasNext} and
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

    private DirectBuffer inputBuffer;
    private int inputOffset;
    private int inputLength;

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
    }

    @Override
    public ProtobufParser wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.inputBuffer = buffer;
        this.inputOffset = offset;
        this.inputLength = length;
        this.started = false;
        this.done = false;
        this.depth = -1;
        this.phase = PHASE_NONE;
        this.deferred = 0;
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
                decodeScalar(field, frames.get(depth).reader);
                phase = PHASE_NONE;
                event = ProtobufEvent.VALUE;
                break;
            case PHASE_RAW_VALUE:
                decodeRaw(frames.get(depth).reader, fieldNumber, wireType);
                phase = PHASE_NONE;
                event = ProtobufEvent.VALUE;
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

    private ProtobufEvent startRoot()
    {
        ProtobufMessage root = schema != null ? schema.message(messageName) : null;
        if (schema != null && root == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        depth = 0;
        Frame frame = frame(0);
        frame.reader.wrap(inputBuffer, inputOffset, inputLength);
        frame.message = root;
        frame.group = false;
        frame.packedLimit = -1;
        boundary();
        slice(inputBuffer, inputOffset, inputLength);
        return ProtobufEvent.START_MESSAGE;
    }

    private ProtobufEvent advance()
    {
        ProtobufEvent event = null;
        while (event == null)
        {
            Frame frame = frames.get(depth);
            if (frame.packedLimit >= 0 && frame.reader.offset() < frame.packedLimit)
            {
                field = frame.packedField;
                fieldNumber = field.number();
                wireType = field.type().wireType();
                phase = PHASE_SCALAR_VALUE;
                event = ProtobufEvent.FIELD;
            }
            else if (frame.packedLimit >= 0)
            {
                frame.packedLimit = -1;
            }
            else if (!frame.reader.hasRemaining())
            {
                event = endFrame();
            }
            else
            {
                event = readField(frame);
            }
        }
        return event;
    }

    private ProtobufEvent readField(
        Frame frame)
    {
        ProtobufEvent event;
        ProtobufReader reader = frame.reader;
        int tag = reader.readVarint32();
        int number = tag >>> 3;
        ProtobufWireType wt = ProtobufWireType.of(tag & 0x7);
        if (frame.message == null)
        {
            field = null;
            fieldNumber = number;
            wireType = wt;
            phase = PHASE_RAW_VALUE;
            event = ProtobufEvent.FIELD;
        }
        else
        {
            ProtobufField fld = frame.message.field(number);
            if (fld == null)
            {
                reader.skipField(number, wt);
                event = null;
            }
            else if (fld.composite())
            {
                event = readComposite(frame, fld, number, wt);
            }
            else if (fld.repeated() && fld.type().packable() && wt == ProtobufWireType.LEN)
            {
                int blockLength = reader.readLength();
                frame.packedLimit = reader.offset() + blockLength;
                frame.packedField = fld;
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
        }
        return event;
    }

    private ProtobufEvent readComposite(
        Frame frame,
        ProtobufField fld,
        int number,
        ProtobufWireType wt)
    {
        ProtobufReader reader = frame.reader;
        if (fld.type() == ProtobufType.GROUP)
        {
            if (wt != ProtobufWireType.SGROUP)
            {
                throw new ProtobufException("field " + number + " expected group");
            }
            regionOffset = reader.offset();
            regionLength = reader.skipGroup(number) - regionOffset;
        }
        else
        {
            if (wt != ProtobufWireType.LEN)
            {
                throw new ProtobufException("field " + number + " expected length-delimited message");
            }
            regionLength = reader.readLength();
            regionOffset = reader.offset();
            reader.skip(regionLength);
        }
        compositeField = fld;
        field = fld;
        fieldNumber = number;
        wireType = wt;
        phase = PHASE_COMPOSITE;
        return ProtobufEvent.FIELD;
    }

    private ProtobufEvent resolveComposite(
        Mode mode)
    {
        ProtobufEvent event;
        if (mode == Mode.SEGMENTED)
        {
            phase = PHASE_NONE;
            field = compositeField;
            fieldNumber = compositeField.number();
            wireType = compositeField.type().wireType();
            slice(inputBuffer, regionOffset, regionLength);
            event = ProtobufEvent.SEGMENT;
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
            frame.reader.wrap(inputBuffer, regionOffset, regionLength);
            frame.message = nested;
            frame.group = group;
            frame.packedLimit = -1;
            boundary();
            slice(inputBuffer, regionOffset, regionLength);
            event = group ? ProtobufEvent.START_GROUP : ProtobufEvent.START_MESSAGE;
        }
        return event;
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
        private final ProtobufReader reader = new ProtobufReader();
        private ProtobufMessage message;
        private boolean group;
        private int packedLimit;
        private ProtobufField packedField;
    }
}
