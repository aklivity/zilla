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
package io.aklivity.zilla.runtime.common.avro.internal;

import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BOOLEAN;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BYTES;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.DOUBLE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ENUM;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIXED;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FLOAT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.LONG;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_KEY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.NULL;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.UNION_BRANCH;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroParsingException;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * The pull cursor ({@link AvroParser}) that decodes the Avro wire into a typed event stream and reads
 * each value in place. It is a pure cursor: {@link AvroPipelineImpl} layers the push pipeline over it,
 * owning the read-only {@code AvroSource} view and the {@code AvroController} the stages see, so a stage
 * cannot disturb the pump.
 */
public final class AvroParserImpl implements AvroParser
{
    private static final int READ_OK = 0;
    private static final int READ_UNDERFLOW = 1;
    private static final int READ_MALFORMED = 2;

    private static final int STEP_CONTINUE = 0;
    private static final int STEP_EVENT = 1;
    private static final int STEP_UNDERFLOW = 2;
    private static final int STEP_REJECTED = 3;

    private enum Phase
    {
        NEW,
        ROUTE,
        BODY,
        SEGMENT,
        END,
        DONE
    }

    private static final DirectBuffer EMPTY = new UnsafeBuffer(0, 0);

    private final AvroNode root;
    private final UnsafeBuffer segmentView;
    private final AvroLocationImpl location;

    private DirectBuffer buffer;
    private int offset;
    private int limit;
    private int progress;
    private long start;
    private boolean last;

    private AvroNode[] nodeStack;
    private int[] stateStack;
    private long[] countStack;
    private int depth;

    private long scratchValue;
    private int scratchNext;

    private Phase phase;
    private boolean segmenting;

    private AvroEvent pending;
    private boolean done;

    private int valueOffset;
    private int valueLength;
    private int valueConsumed;
    private int valueRemaining;
    private int deferred;
    private boolean valueStreaming;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String string;
    private String field;
    private AvroNode cursorType;

    public AvroParserImpl(
        AvroSchema schema)
    {
        this.root = (AvroNode) schema.type();
        this.segmentView = new UnsafeBuffer(0, 0);
        this.location = new AvroLocationImpl();
        this.nodeStack = new AvroNode[16];
        this.stateStack = new int[16];
        this.countStack = new long[16];
        reset();
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        start += progress - this.offset;
        this.buffer = buffer;
        this.offset = offset;
        this.progress = offset;
        this.limit = limit;
        this.last = last;
    }

    @Override
    public boolean hasNext()
    {
        if (pending == null && !done)
        {
            advance(AvroParser.Mode.STRUCTURED);
        }
        return pending != null;
    }

    @Override
    public AvroEvent nextEvent(
        AvroParser.Mode mode)
    {
        if (pending == null && !done)
        {
            advance(mode);
        }
        AvroEvent event = pending;
        pending = null;
        return event;
    }

    @Override
    public AvroType type()
    {
        return cursorType;
    }

    @Override
    public void reset()
    {
        depth = 0;
        offset = 0;
        progress = 0;
        limit = 0;
        start = 0;
        buffer = EMPTY;
        phase = Phase.NEW;
        pending = null;
        cursorType = null;
        done = false;
        valueRemaining = 0;
        valueConsumed = 0;
        deferred = 0;
        valueStreaming = false;
        segmenting = false;
        push(root);
    }

    @Override
    public int remaining()
    {
        return limit - progress;
    }

    private long position()
    {
        return start + (progress - offset);
    }

    private void advance(
        AvroParser.Mode mode)
    {
        boolean producing = true;
        while (producing)
        {
            switch (phase)
            {
            case NEW:
                location.locate(depth, position());
                clearValue();
                cursorType = root;
                pending = START_MESSAGE;
                phase = Phase.ROUTE;
                producing = false;
                break;
            case ROUTE:
                if (mode == AvroParser.Mode.SEGMENTED)
                {
                    phase = Phase.SEGMENT;
                    segmenting = true;
                }
                else
                {
                    phase = Phase.BODY;
                }
                break;
            case BODY:
                if (depth == 0)
                {
                    phase = Phase.END;
                }
                else
                {
                    location.locate(depth, position());
                    int step = step(nodeStack[depth - 1]);
                    if (step == STEP_EVENT || step == STEP_UNDERFLOW)
                    {
                        producing = false;
                    }
                    else if (step == STEP_REJECTED)
                    {
                        throw new AvroValidationException("malformed Avro binary");
                    }
                }
                break;
            case SEGMENT:
                location.locate(depth, position());
                int segment = segmentStep();
                if (segment == STEP_EVENT || segment == STEP_UNDERFLOW)
                {
                    producing = false;
                }
                else if (segment == STEP_REJECTED)
                {
                    throw new AvroValidationException("malformed Avro binary");
                }
                break;
            case END:
                location.locate(depth, position());
                clearValue();
                cursorType = null;
                pending = END_MESSAGE;
                phase = Phase.DONE;
                producing = false;
                break;
            default:
                done = true;
                producing = false;
                break;
            }
        }
        if (pending == null && !done && last)
        {
            throw new AvroParsingException("truncated datum");
        }
    }

    private int segmentStep()
    {
        int result;
        int scanStart = progress;
        int scan = STEP_CONTINUE;
        while (scan == STEP_CONTINUE && depth > 0)
        {
            scan = step(nodeStack[depth - 1]);
        }
        if (scan == STEP_REJECTED)
        {
            result = STEP_REJECTED;
        }
        else if (depth == 0)
        {
            // the whole datum is consumed: the final SEGMENT (deferredBytes 0) ends the run, then END
            setSegment(scanStart, progress - scanStart);
            deferred = 0;
            pending = SEGMENT;
            phase = Phase.END;
            result = STEP_EVENT;
        }
        else if (progress > scanStart)
        {
            // more of the datum to come, total unknown
            setSegment(scanStart, progress - scanStart);
            deferred = AvroSource.UNBOUNDED;
            pending = SEGMENT;
            result = STEP_EVENT;
        }
        else
        {
            result = STEP_UNDERFLOW;
        }
        cursorType = null;
        return result;
    }

    private int step(
        AvroNode node)
    {
        int result;
        cursorType = node;
        deferred = 0;
        switch (node.kind)
        {
        case NULL:
            result = stepLeaf(NULL);
            break;
        case BOOLEAN:
            result = stepBoolean();
            break;
        case INT:
        case LONG:
            result = stepInteger(node);
            break;
        case FLOAT:
            result = stepFloat();
            break;
        case DOUBLE:
            result = stepDouble();
            break;
        case BYTES:
        case STRING:
            result = stepBytes(node);
            break;
        case FIXED:
            result = stepFixed(node);
            break;
        case ENUM:
            result = stepEnum(node);
            break;
        case RECORD:
            result = stepRecord(node);
            break;
        case ARRAY:
            result = stepArray(node);
            break;
        case MAP:
            result = stepMap(node);
            break;
        case UNION:
            result = stepUnion(node);
            break;
        default:
            result = STEP_REJECTED;
            break;
        }
        return result;
    }

    private int stepLeaf(
        AvroEvent event)
    {
        clearValue();
        pop();
        return produced(event);
    }

    private int stepBoolean()
    {
        int result;
        if (progress >= limit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            int b = buffer.getByte(progress) & 0xff;
            if (b > 1)
            {
                result = STEP_REJECTED;
            }
            else
            {
                progress++;
                booleanValue = b != 0;
                clearValue();
                pop();
                result = produced(BOOLEAN);
            }
        }
        return result;
    }

    private int stepInteger(
        AvroNode node)
    {
        boolean isInt = node.kind == AvroKind.INT;
        int read = readVarint(progress, isInt ? 5 : 10);
        int result;
        if (read == READ_OK)
        {
            progress = scratchNext;
            long decoded = zigzag(scratchValue);
            AvroEvent event;
            if (isInt)
            {
                intValue = (int) decoded;
                event = INT;
            }
            else
            {
                longValue = decoded;
                event = LONG;
            }
            clearValue();
            pop();
            result = produced(event);
        }
        else
        {
            result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
        }
        return result;
    }

    private int stepFloat()
    {
        int result;
        if (progress + Float.BYTES > limit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            floatValue = buffer.getFloat(progress, LITTLE_ENDIAN);
            progress += Float.BYTES;
            clearValue();
            pop();
            result = produced(FLOAT);
        }
        return result;
    }

    private int stepDouble()
    {
        int result;
        if (progress + Double.BYTES > limit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            doubleValue = buffer.getDouble(progress, LITTLE_ENDIAN);
            progress += Double.BYTES;
            clearValue();
            pop();
            result = produced(DOUBLE);
        }
        return result;
    }

    private int stepBytes(
        AvroNode node)
    {
        int result;
        if (valueStreaming)
        {
            result = emitValueChunk(node);
        }
        else
        {
            int read = readVarint(progress, 10);
            if (read == READ_OK)
            {
                long length = zigzag(scratchValue);
                if (length < 0 || length > Integer.MAX_VALUE)
                {
                    result = STEP_REJECTED;
                }
                else
                {
                    progress = scratchNext;
                    valueRemaining = (int) length;
                    valueStreaming = true;
                    result = emitValueChunk(node);
                }
            }
            else
            {
                result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
            }
        }
        return result;
    }

    private int stepFixed(
        AvroNode node)
    {
        if (!valueStreaming)
        {
            valueRemaining = node.size;
            valueStreaming = true;
        }
        return emitValueChunk(node);
    }

    // emit the next chunk of a length-delimited value: the bytes available now (trimmed to a UTF-8 char
    // boundary for a non-final string chunk, so getString() never splits a character), with the remaining
    // payload count exposed via deferredBytes(); a value that fits the window is a single chunk
    private int emitValueChunk(
        AvroNode node)
    {
        int result;
        int available = limit - progress;
        if (available <= 0 && valueRemaining > 0)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            int chunk = Math.min(available, valueRemaining);
            if (chunk < valueRemaining && !segmenting)
            {
                if (node.kind == AvroKind.STRING)
                {
                    chunk = utf8Boundary(progress, chunk);
                }
                else if (node.kind == AvroKind.BYTES || node.kind == AvroKind.FIXED)
                {
                    // base64 renders a whole 3-byte group at a time, so a non-final bytes/fixed chunk must end on
                    // a 3-byte boundary — the bytes analog of utf8Boundary — withholding a 1-2 byte tail until the
                    // next input window so a base64 group is never split across windows
                    chunk = chunk / 3 * 3;
                }
            }
            if (chunk == 0 && valueRemaining > 0)
            {
                result = STEP_UNDERFLOW;
            }
            else
            {
                setValueBytes(progress, chunk);
                progress += chunk;
                valueRemaining -= chunk;
                deferred = valueRemaining;
                if (valueRemaining == 0)
                {
                    valueStreaming = false;
                    pop();
                }
                result = produced(valueEvent(node));
            }
        }
        return result;
    }

    private int utf8Boundary(
        int start,
        int length)
    {
        int end = start + length;
        int p = end;
        while (p > start && (buffer.getByte(p - 1) & 0xc0) == 0x80)
        {
            p--;
        }
        if (p > start)
        {
            int lead = buffer.getByte(p - 1) & 0xff;
            int need = utf8Length(lead);
            p = (p - 1) + need <= end ? end : p - 1;
        }
        return p - start;
    }

    private static int utf8Length(
        int lead)
    {
        int length;
        if ((lead & 0x80) == 0)
        {
            length = 1;
        }
        else if ((lead & 0xe0) == 0xc0)
        {
            length = 2;
        }
        else if ((lead & 0xf0) == 0xe0)
        {
            length = 3;
        }
        else if ((lead & 0xf8) == 0xf0)
        {
            length = 4;
        }
        else
        {
            length = 1;
        }
        return length;
    }

    private static AvroEvent valueEvent(
        AvroNode node)
    {
        AvroEvent event;
        switch (node.kind)
        {
        case STRING:
            event = STRING;
            break;
        case BYTES:
            event = BYTES;
            break;
        default:
            event = FIXED;
            break;
        }
        return event;
    }

    private int stepEnum(
        AvroNode node)
    {
        int read = readVarint(progress, 5);
        int result;
        if (read == READ_OK)
        {
            long index = zigzag(scratchValue);
            if (index < 0 || index >= node.symbols.length)
            {
                result = STEP_REJECTED;
            }
            else
            {
                progress = scratchNext;
                intValue = (int) index;
                string = node.symbols[(int) index];
                valueLength = 0;
                pop();
                result = produced(ENUM);
            }
        }
        else
        {
            result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
        }
        return result;
    }

    private int stepRecord(
        AvroNode node)
    {
        int frame = depth - 1;
        int state = stateStack[frame];
        int result;
        if (state == 0)
        {
            stateStack[frame] = 1;
            clearValue();
            result = produced(START_RECORD);
        }
        else if (state <= node.fieldNames.length)
        {
            int index = state - 1;
            stateStack[frame] = state + 1;
            field = node.fieldNames[index];
            cursorType = node.children[index];
            string = null;
            valueLength = 0;
            result = produced(FIELD_NAME);
            push(node.children[index]);
        }
        else
        {
            pop();
            clearValue();
            result = produced(END_RECORD);
        }
        return result;
    }

    private int stepArray(
        AvroNode node)
    {
        int frame = depth - 1;
        int state = stateStack[frame];
        int result;
        if (state == 0)
        {
            stateStack[frame] = 1;
            clearValue();
            result = produced(START_ARRAY);
        }
        else if (state == 1)
        {
            result = stepBlockHeader(frame, END_ARRAY);
        }
        else if (countStack[frame] > 0)
        {
            countStack[frame]--;
            push(node.children[0]);
            result = STEP_CONTINUE;
        }
        else
        {
            stateStack[frame] = 1;
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int stepMap(
        AvroNode node)
    {
        int frame = depth - 1;
        int state = stateStack[frame];
        int result;
        if (state == 0)
        {
            stateStack[frame] = 1;
            clearValue();
            result = produced(START_MAP);
        }
        else if (state == 1)
        {
            result = stepBlockHeader(frame, END_MAP);
        }
        else if (state == 2)
        {
            result = stepMapKey(frame);
        }
        else
        {
            countStack[frame]--;
            stateStack[frame] = 2;
            push(node.children[0]);
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int stepMapKey(
        int frame)
    {
        int result;
        if (countStack[frame] > 0)
        {
            int read = readVarint(progress, 10);
            if (read == READ_OK)
            {
                long length = zigzag(scratchValue);
                int dataStart = scratchNext;
                if (length < 0 || length > Integer.MAX_VALUE)
                {
                    result = STEP_REJECTED;
                }
                else if (dataStart + length > limit)
                {
                    result = STEP_UNDERFLOW;
                }
                else
                {
                    setValueBytes(dataStart, (int) length);
                    progress = dataStart + (int) length;
                    cursorType = nodeStack[frame].children[0];
                    stateStack[frame] = 3;
                    result = produced(MAP_KEY);
                }
            }
            else
            {
                result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
            }
        }
        else
        {
            stateStack[frame] = 1;
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int stepBlockHeader(
        int frame,
        AvroEvent endEvent)
    {
        int read = readBlockHeader(progress);
        int result;
        if (read == READ_OK)
        {
            progress = scratchNext;
            if (scratchValue == 0)
            {
                pop();
                clearValue();
                result = produced(endEvent);
            }
            else
            {
                countStack[frame] = scratchValue;
                stateStack[frame] = 2;
                result = STEP_CONTINUE;
            }
        }
        else
        {
            result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
        }
        return result;
    }

    private int stepUnion(
        AvroNode node)
    {
        int frame = depth - 1;
        int state = stateStack[frame];
        int result;
        if (state == 0)
        {
            int read = readVarint(progress, 10);
            if (read == READ_OK)
            {
                long index = zigzag(scratchValue);
                if (index < 0 || index >= node.children.length)
                {
                    result = STEP_REJECTED;
                }
                else
                {
                    progress = scratchNext;
                    intValue = (int) index;
                    cursorType = node.children[(int) index];
                    clearValue();
                    stateStack[frame] = 1;
                    result = produced(UNION_BRANCH);
                    push(node.children[(int) index]);
                }
            }
            else
            {
                result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
            }
        }
        else
        {
            pop();
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int produced(
        AvroEvent event)
    {
        int result;
        if (segmenting)
        {
            result = STEP_CONTINUE;
        }
        else
        {
            pending = event;
            result = STEP_EVENT;
        }
        return result;
    }

    private void setValueBytes(
        int offset,
        int length)
    {
        this.valueOffset = offset;
        this.valueLength = length;
        this.valueConsumed = 0;
        this.string = null;
    }

    private void setSegment(
        int offset,
        int length)
    {
        this.valueOffset = offset;
        this.valueLength = length;
        this.valueConsumed = 0;
    }

    private void clearValue()
    {
        this.string = null;
        this.valueLength = 0;
        this.valueConsumed = 0;
        this.deferred = 0;
    }

    @Override
    public boolean getBoolean()
    {
        return booleanValue;
    }

    @Override
    public int getInt()
    {
        return intValue;
    }

    @Override
    public long getLong()
    {
        return longValue;
    }

    @Override
    public float getFloat()
    {
        return floatValue;
    }

    @Override
    public double getDouble()
    {
        return doubleValue;
    }

    @Override
    public String getString()
    {
        String value = string;
        if (value == null && valueLength > 0)
        {
            value = decode();
        }
        return value;
    }

    @Override
    public String getField()
    {
        return field;
    }

    @Override
    public String getKey()
    {
        return valueLength > 0 ? decode() : null;
    }

    @Override
    public DirectBuffer getSegment()
    {
        segmentView.wrap(buffer, valueOffset + valueConsumed, valueLength - valueConsumed);
        return segmentView;
    }

    @Override
    public void consumed(
        int sourceBytes)
    {
        valueConsumed += sourceBytes;
    }

    @Override
    public int deferredBytes()
    {
        return deferred;
    }

    @Override
    public AvroLocation getLocation()
    {
        return location;
    }

    private String decode()
    {
        byte[] dst = new byte[valueLength];
        buffer.getBytes(valueOffset, dst);
        return new String(dst, UTF_8);
    }

    private int readVarint(
        int from,
        int maxBytes)
    {
        long result = 0;
        int shift = 0;
        int p = from;
        int status = READ_UNDERFLOW;
        boolean reading = true;
        while (reading)
        {
            if (p >= limit)
            {
                status = READ_UNDERFLOW;
                reading = false;
            }
            else
            {
                int b = buffer.getByte(p) & 0xff;
                p++;
                result |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0)
                {
                    scratchValue = result;
                    scratchNext = p;
                    status = READ_OK;
                    reading = false;
                }
                else if (p - from >= maxBytes)
                {
                    status = READ_MALFORMED;
                    reading = false;
                }
                else
                {
                    shift += 7;
                }
            }
        }
        return status;
    }

    private int readBlockHeader(
        int from)
    {
        int read = readVarint(from, 10);
        int status = read;
        if (read == READ_OK)
        {
            long count = zigzag(scratchValue);
            if (count < 0)
            {
                int sizeRead = readVarint(scratchNext, 10);
                status = sizeRead;
                if (sizeRead == READ_OK)
                {
                    scratchValue = -count;
                }
            }
            else
            {
                scratchValue = count;
            }
        }
        return status;
    }

    private void push(
        AvroNode node)
    {
        if (depth == nodeStack.length)
        {
            grow();
        }
        nodeStack[depth] = node;
        stateStack[depth] = 0;
        countStack[depth] = 0;
        depth++;
    }

    private void pop()
    {
        depth--;
    }

    private void grow()
    {
        int capacity = nodeStack.length * 2;
        AvroNode[] nodes = new AvroNode[capacity];
        int[] states = new int[capacity];
        long[] counts = new long[capacity];
        System.arraycopy(nodeStack, 0, nodes, 0, depth);
        System.arraycopy(stateStack, 0, states, 0, depth);
        System.arraycopy(countStack, 0, counts, 0, depth);
        nodeStack = nodes;
        stateStack = states;
        countStack = counts;
    }

    private static long zigzag(
        long value)
    {
        return (value >>> 1) ^ -(value & 1);
    }
}
