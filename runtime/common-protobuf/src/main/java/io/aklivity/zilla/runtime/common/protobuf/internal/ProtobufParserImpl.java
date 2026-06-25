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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufLocation;
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
    private static final int PHASE_LEAF = 4;

    private final ProtobufSchema schema;
    private final String messageName;
    private final List<Frame> frames;
    private final ProtobufReader reader;
    private final ProtobufLocation location = new ProtobufLocation()
    {
        @Override
        public long getStreamOffset()
        {
            return reader.position();
        }
    };

    private boolean started;
    private boolean done;
    private int depth;
    private int phase;
    // the last event nextEvent() returned: the basis for the streaming-value accessor asserts, which document
    // the event each accessor is legitimately positioned on (mirrors JsonParserImpl's lastEvent)
    private ProtobufEvent lastEvent;

    private ProtobufField compositeField;
    private int regionOffset;
    private int regionLength;

    private ProtobufField field;
    private int fieldNumber;
    private ProtobufWireType wireType;
    private long longValue;
    private double doubleValue;
    private float floatValue;
    private final UnsafeBuffer segment = new UnsafeBufferEx(new byte[0]);
    private DirectBufferEx segmentBuffer;
    private int segmentOffset;
    private int segmentLength;
    private int deferred;
    private int leafRemaining;
    private int leafUncommitted;
    private boolean leafString;
    private boolean leafBytes;
    private int skipRemaining;

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
    public boolean identity()
    {
        return true;
    }

    @Override
    public ProtobufParser wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        reader.wrap(buffer, offset, limit, last);
        this.started = false;
        this.done = false;
        this.depth = -1;
        this.phase = PHASE_NONE;
        this.deferred = 0;
        this.leafRemaining = -1;
        this.leafUncommitted = 0;
        this.skipRemaining = 0;
        this.lastEvent = null;
        return this;
    }

    @Override
    public ProtobufParser resume(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        // the driver re-presents the unconsumed tail at the front of the next window: any streaming-leaf bytes
        // delivered-but-not-committed in the prior window are part of that tail and are re-read fresh here, so
        // they must not be committed against this window — drop the pending commit
        this.leafUncommitted = 0;
        reader.resume(buffer, offset, limit, last);
        return this;
    }

    @Override
    public boolean hasNext()
    {
        return !done;
    }

    @Override
    public ProtobufLocation getLocation()
    {
        return location;
    }

    @Override
    public int remaining()
    {
        return reader.remaining();
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
            case PHASE_LEAF:
                event = leaf();
                break;
            default:
                event = advance();
                break;
            }
        }
        // track the delivered event (null on starvation) as the basis for the accessor asserts
        if (event != null)
        {
            lastEvent = event;
        }
        return event;
    }

    @Override
    public ProtobufField field()
    {
        // positions on a field (FIELD) and is re-read on its VALUE to discover the value's declared type
        assert lastEvent == ProtobufEvent.FIELD || lastEvent == ProtobufEvent.VALUE;
        return field;
    }

    @Override
    public ProtobufMessage message()
    {
        // the current frame's message, freshly meaningful at a frame open (START_MESSAGE / START_GROUP)
        assert lastEvent == ProtobufEvent.START_MESSAGE || lastEvent == ProtobufEvent.START_GROUP;
        return depth >= 0 ? frames.get(depth).message : null;
    }

    @Override
    public int fieldNumber()
    {
        // the schema-free path identifies a field by number at FIELD and on its VALUE; a raw composite delivered
        // as a SEGMENT carries its field number too
        assert lastEvent == ProtobufEvent.FIELD || lastEvent == ProtobufEvent.VALUE || lastEvent == ProtobufEvent.SEGMENT;
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
        // a decoded scalar is delivered on its VALUE event
        assert lastEvent == ProtobufEvent.VALUE;
        return longValue;
    }

    @Override
    public double doubleValue()
    {
        // a decoded scalar is delivered on its VALUE event
        assert lastEvent == ProtobufEvent.VALUE;
        return doubleValue;
    }

    @Override
    public float floatValue()
    {
        // a decoded scalar is delivered on its VALUE event
        assert lastEvent == ProtobufEvent.VALUE;
        return floatValue;
    }

    @Override
    public DirectBufferEx segment()
    {
        // the value/segment slice on a VALUE (length-delimited leaf or raw scalar) or a SEGMENT (raw composite),
        // and the message extent on START_MESSAGE so a sink can size the enclosing nested record
        assert lastEvent == ProtobufEvent.VALUE || lastEvent == ProtobufEvent.SEGMENT ||
            lastEvent == ProtobufEvent.START_MESSAGE;
        return segment;
    }

    @Override
    public int deferredBytes()
    {
        // how much of a streaming value is still to come: reported on each VALUE chunk and each SEGMENT chunk
        assert lastEvent == ProtobufEvent.VALUE || lastEvent == ProtobufEvent.SEGMENT;
        return deferred;
    }

    @Override
    public void consumed(
        int sourceBytes)
    {
        // the pushback advances the read cursor within a length-delimited value, the only state where advancing
        // is meaningful: a VALUE leaf chunk or a raw composite SEGMENT
        assert lastEvent == ProtobufEvent.VALUE || lastEvent == ProtobufEvent.SEGMENT;
        if (leafRemaining >= 0)
        {
            // a streaming leaf chunk is delivered uncommitted: commit exactly what the sink took here, advancing
            // the read cursor by that and no more. Any sub-unit tail the sink left (e.g. a base64 group short of
            // 3 bytes) stays uncommitted, so remaining() reports it and the driver re-presents it contiguous with
            // the next window — no eager over-commit, no rewind
            reader.skip(sourceBytes);
            leafRemaining -= sourceBytes;
            leafUncommitted -= sourceBytes;
            if (leafRemaining == 0)
            {
                // the leaf body is fully consumed; the next leaf() reads a fresh length prefix
                leafRemaining = -1;
                leafUncommitted = 0;
            }
        }
        // re-wrap the current value slice so segment() re-exposes the remainder within this same window under
        // output back-pressure (SUSPENDED/resume); for a whole leaf the read cursor was committed at delivery
        // and is left untouched here
        segmentOffset += sourceBytes;
        segmentLength -= sourceBytes;
        segment.wrap(segmentBuffer, segmentOffset, segmentLength);
    }

    private ProtobufEvent value()
    {
        phase = PHASE_NONE;
        deferred = 0;
        return ProtobufEvent.VALUE;
    }

    private ProtobufEvent leaf()
    {
        ProtobufEvent event;
        if (leafUncommitted > 0)
        {
            // a prior non-final chunk was delivered uncommitted (deferred > 0). A bounded sink commits it via
            // consumed(); a direct caller that took the whole slice does not, so commit it here before marking —
            // advancing the read cursor and the body remaining by exactly the bytes delivered last time
            reader.skip(leafUncommitted);
            leafRemaining -= leafUncommitted;
            leafUncommitted = 0;
        }
        reader.mark();
        if (leafRemaining < 0)
        {
            int len = reader.readVarint32();
            if (reader.starved())
            {
                event = starve();
            }
            else if (len < 0)
            {
                throw new ProtobufException("negative length " + len);
            }
            else if (len <= reader.available())
            {
                // the whole leaf is present in this window (deferred == 0): commit the read cursor past it now so
                // a direct caller that takes the whole slice without calling consumed() still advances, and a
                // bounded sink streams it across output back-pressure via the re-wrapped slice in consumed() —
                // there is no future-input tail here, so the cursor is at the value end either way
                slice(reader.buffer(), reader.offset(), len);
                reader.skip(len);
                longValue = len;
                deferred = 0;
                phase = PHASE_NONE;
                event = ProtobufEvent.VALUE;
            }
            else if (reader.last())
            {
                throw new ProtobufException("truncated field: need " + len + " bytes");
            }
            else
            {
                leafRemaining = len;
                // re-checkpoint at the body so a body starvation carries only the body, not the length prefix
                reader.mark();
                event = leafChunk();
            }
        }
        else
        {
            event = leafChunk();
        }
        return event;
    }

    private ProtobufEvent leafChunk()
    {
        ProtobufEvent event;
        int available = reader.available();
        if (leafRemaining <= available)
        {
            // the whole remaining body is present (deferred == 0): commit the read cursor past it now so a direct
            // caller that takes the whole slice without consumed() still advances; mark the leaf complete so
            // consumed() treats this as a whole leaf (re-wrapping the slice for output back-pressure only)
            slice(reader.buffer(), reader.offset(), leafRemaining);
            reader.skip(leafRemaining);
            leafRemaining = -1;
            deferred = 0;
            phase = PHASE_NONE;
            event = ProtobufEvent.VALUE;
        }
        else
        {
            int chunk;
            if (leafString)
            {
                chunk = utf8SafeLength(reader.buffer(), reader.offset(), available);
            }
            else if (leafBytes)
            {
                chunk = base64SafeLength(available);
            }
            else
            {
                // schema-free LEN body streams verbatim (wire -> wire), no rendering unit to align to
                chunk = available;
            }
            if (chunk == 0)
            {
                // only a partial rendering unit present (a partial UTF-8 code point, or 1-2 leaf bytes short of a
                // whole base64 group); carry it and wait for the next window
                event = starve();
            }
            else
            {
                // deliver this window's leaf bytes uncommitted: the read cursor and the body remaining advance by
                // exactly what the sink takes (consumed()) or, for a direct caller, by the whole chunk on the next
                // leafChunk re-entry — so a sub-unit tail the sink leaves stays uncommitted for the next window
                slice(reader.buffer(), reader.offset(), chunk);
                leafUncommitted = chunk;
                deferred = leafRemaining - chunk;
                event = ProtobufEvent.VALUE;
            }
        }
        return event;
    }

    private ProtobufEvent starve()
    {
        // leave the partial unit unconsumed at the committed position; the driver retains and re-presents it
        reader.rewind();
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
            if (skipRemaining > 0)
            {
                // discard an unknown length-delimited field a window at a time, committing as we go
                int available = reader.available();
                if (skipRemaining <= available)
                {
                    reader.skip(skipRemaining);
                    skipRemaining = 0;
                }
                else if (reader.last())
                {
                    throw new ProtobufException("truncated field");
                }
                else
                {
                    reader.skip(available);
                    skipRemaining -= available;
                    starving = true;
                }
            }
            else if (frame.packedEnd >= 0L && reader.position() < frame.packedEnd)
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
                leafString = false;
                leafBytes = false;
                phase = wt == ProtobufWireType.LEN ? PHASE_LEAF : PHASE_RAW_VALUE;
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
        if (fld == null && wt == ProtobufWireType.LEN)
        {
            // unknown length-delimited field: read its length, then stream-discard the body across windows
            int len = reader.readVarint32();
            if (!reader.starved())
            {
                if (len < 0)
                {
                    throw new ProtobufException("negative length " + len);
                }
                skipRemaining = len;
            }
            event = null;
        }
        else if (fld == null)
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
            boolean len = fld.type() == ProtobufType.STRING || fld.type() == ProtobufType.BYTES;
            leafString = fld.type() == ProtobufType.STRING;
            leafBytes = fld.type() == ProtobufType.BYTES;
            phase = len ? PHASE_LEAF : PHASE_SCALAR_VALUE;
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
            event = resolveSegment();
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

    private ProtobufEvent resolveSegment()
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
        deferred = 0;
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
        deferred = 0;
    }

    // The proto3-JSON bytes rendering unit: base64 encodes a whole 3-byte group at a time, so a non-final bytes
    // leaf chunk must end on a 3-byte boundary — the bytes analog of UTF-8 code-point alignment. A 1-2 byte tail
    // short of a group is withheld (returned length excludes it) so it streams in contiguous with the next window,
    // exactly as utf8SafeLength withholds a partial code point. Harmless for wire -> wire, where bytes are verbatim
    // and this length is never consulted (only genuine BYTES leaves are aligned here).
    private static int base64SafeLength(
        int length)
    {
        return length / 3 * 3;
    }

    private static int utf8SafeLength(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        int safe = length;
        int back = 0;
        while (back < length && back < 4 && (buffer.getByte(offset + length - 1 - back) & 0xc0) == 0x80)
        {
            back++;
        }
        if (back >= length)
        {
            // the whole window is continuation bytes (no lead present) — emit nothing yet
            safe = 0;
        }
        else
        {
            int leadIndex = length - 1 - back;
            int codePointLength = utf8CodePointLength(buffer.getByte(offset + leadIndex) & 0xff);
            // trim the trailing code point only when it is not fully present
            safe = leadIndex + codePointLength <= length ? length : leadIndex;
        }
        return safe;
    }

    private static int utf8CodePointLength(
        int lead)
    {
        int codePointLength;
        if ((lead & 0x80) == 0)
        {
            codePointLength = 1;
        }
        else if ((lead & 0xe0) == 0xc0)
        {
            codePointLength = 2;
        }
        else if ((lead & 0xf0) == 0xe0)
        {
            codePointLength = 3;
        }
        else if ((lead & 0xf8) == 0xf0)
        {
            codePointLength = 4;
        }
        else
        {
            // invalid lead byte; treat as a single byte so malformed input still makes progress
            codePointLength = 1;
        }
        return codePointLength;
    }

    private void slice(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        segmentBuffer = buffer;
        segmentOffset = offset;
        segmentLength = length;
        segment.wrap(buffer, offset, length);
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
