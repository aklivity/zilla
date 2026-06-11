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
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.CONTINUE_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.DOUBLE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ENUM;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIXED;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FLOAT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.LONG;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_KEY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.NULL;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.UNION_BRANCH;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroStream;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * The pull parser ({@link AvroParser}) that is also the {@link AvroSource} the pipeline layers over
 * it and the {@link AvroController} steering segment delivery. The interfaces stay distinct; this one
 * class provides all three so the pipeline can hand the same instance to a sink as source and control.
 */
final class AvroParserImpl implements AvroParser, AvroSource, AvroController
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

    private final AvroNode root;
    private final MutableDirectBuffer work;
    private final UnsafeBuffer segmentView;
    private final AvroLocationImpl location;

    private int workLimit;
    private int pos;

    private AvroNode[] nodeStack;
    private int[] stateStack;
    private long[] countStack;
    private int depth;

    private long scratchValue;
    private int scratchNext;

    private Phase phase;
    private boolean segmentRequested;
    private boolean segmenting;
    private boolean segmentStarted;
    private boolean segmentPendingEnd;

    private AvroEvent pending;
    private boolean done;

    private int valueOffset;
    private int valueLength;
    private boolean booleanValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private String string;
    private String field;

    AvroParserImpl(
        AvroNode root)
    {
        this.root = root;
        this.work = new ExpandableArrayBuffer();
        this.segmentView = new UnsafeBuffer(0, 0);
        this.location = new AvroLocationImpl();
        this.nodeStack = new AvroNode[16];
        this.stateStack = new int[16];
        this.countStack = new long[16];
        reset();
    }

    @Override
    public AvroStream stream()
    {
        return new AvroStreamImpl(this);
    }

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        if (pos > 0)
        {
            int remaining = workLimit - pos;
            if (remaining > 0)
            {
                work.putBytes(0, work, pos, remaining);
            }
            workLimit = remaining;
            pos = 0;
        }
        work.putBytes(workLimit, buffer, offset, length);
        workLimit += length;
    }

    @Override
    public boolean hasNext()
    {
        if (pending == null && !done)
        {
            advance();
        }
        return pending != null;
    }

    @Override
    public AvroEvent nextEvent()
    {
        AvroEvent event = pending;
        pending = null;
        return event;
    }

    @Override
    public void segmentable()
    {
        segmentRequested = true;
    }

    void reset()
    {
        depth = 0;
        workLimit = 0;
        pos = 0;
        phase = Phase.NEW;
        pending = null;
        done = false;
        segmentRequested = false;
        segmenting = false;
        segmentStarted = false;
        segmentPendingEnd = false;
        push(root);
    }

    boolean complete()
    {
        return done;
    }

    private void advance()
    {
        boolean producing = true;
        while (producing)
        {
            switch (phase)
            {
            case NEW:
                location.locate(depth, pos);
                clearValue();
                pending = START_MESSAGE;
                phase = Phase.ROUTE;
                producing = false;
                break;
            case ROUTE:
                if (segmentRequested)
                {
                    phase = Phase.SEGMENT;
                    segmenting = true;
                    segmentStarted = false;
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
                    location.locate(depth, pos);
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
                location.locate(depth, pos);
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
                location.locate(depth, pos);
                clearValue();
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
    }

    private int segmentStep()
    {
        int result;
        if (segmentPendingEnd)
        {
            segmentPendingEnd = false;
            setSegment(pos, 0);
            pending = END_SEGMENT;
            phase = Phase.END;
            result = STEP_EVENT;
        }
        else
        {
            int scanStart = pos;
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
                setSegment(scanStart, pos - scanStart);
                if (segmentStarted)
                {
                    pending = END_SEGMENT;
                    phase = Phase.END;
                }
                else
                {
                    segmentStarted = true;
                    segmentPendingEnd = true;
                    pending = START_SEGMENT;
                }
                result = STEP_EVENT;
            }
            else if (pos > scanStart)
            {
                setSegment(scanStart, pos - scanStart);
                pending = segmentStarted ? CONTINUE_SEGMENT : START_SEGMENT;
                segmentStarted = true;
                result = STEP_EVENT;
            }
            else
            {
                result = STEP_UNDERFLOW;
            }
        }
        return result;
    }

    private int step(
        AvroNode node)
    {
        int result;
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
        if (pos >= workLimit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            int b = work.getByte(pos) & 0xff;
            if (b > 1)
            {
                result = STEP_REJECTED;
            }
            else
            {
                pos++;
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
        int read = readVarint(pos, isInt ? 5 : 10);
        int result;
        if (read == READ_OK)
        {
            pos = scratchNext;
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
        if (pos + Float.BYTES > workLimit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            floatValue = work.getFloat(pos, LITTLE_ENDIAN);
            pos += Float.BYTES;
            clearValue();
            pop();
            result = produced(FLOAT);
        }
        return result;
    }

    private int stepDouble()
    {
        int result;
        if (pos + Double.BYTES > workLimit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            doubleValue = work.getDouble(pos, LITTLE_ENDIAN);
            pos += Double.BYTES;
            clearValue();
            pop();
            result = produced(DOUBLE);
        }
        return result;
    }

    private int stepBytes(
        AvroNode node)
    {
        int read = readVarint(pos, 10);
        int result;
        if (read == READ_OK)
        {
            long length = zigzag(scratchValue);
            int dataStart = scratchNext;
            if (length < 0 || length > Integer.MAX_VALUE)
            {
                result = STEP_REJECTED;
            }
            else if (dataStart + length > workLimit)
            {
                result = STEP_UNDERFLOW;
            }
            else
            {
                setValueBytes(dataStart, (int) length);
                pos = dataStart + (int) length;
                pop();
                result = produced(node.kind == AvroKind.STRING ? STRING : BYTES);
            }
        }
        else
        {
            result = read == READ_UNDERFLOW ? STEP_UNDERFLOW : STEP_REJECTED;
        }
        return result;
    }

    private int stepFixed(
        AvroNode node)
    {
        int result;
        if (pos + node.size > workLimit)
        {
            result = STEP_UNDERFLOW;
        }
        else
        {
            setValueBytes(pos, node.size);
            pos += node.size;
            pop();
            result = produced(FIXED);
        }
        return result;
    }

    private int stepEnum(
        AvroNode node)
    {
        int read = readVarint(pos, 5);
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
                pos = scratchNext;
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
            int read = readVarint(pos, 10);
            if (read == READ_OK)
            {
                long length = zigzag(scratchValue);
                int dataStart = scratchNext;
                if (length < 0 || length > Integer.MAX_VALUE)
                {
                    result = STEP_REJECTED;
                }
                else if (dataStart + length > workLimit)
                {
                    result = STEP_UNDERFLOW;
                }
                else
                {
                    setValueBytes(dataStart, (int) length);
                    pos = dataStart + (int) length;
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
        int read = readBlockHeader(pos);
        int result;
        if (read == READ_OK)
        {
            pos = scratchNext;
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
            int read = readVarint(pos, 10);
            if (read == READ_OK)
            {
                long index = zigzag(scratchValue);
                if (index < 0 || index >= node.children.length)
                {
                    result = STEP_REJECTED;
                }
                else
                {
                    pos = scratchNext;
                    intValue = (int) index;
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
        this.string = null;
    }

    private void setSegment(
        int offset,
        int length)
    {
        this.valueOffset = offset;
        this.valueLength = length;
    }

    private void clearValue()
    {
        this.string = null;
        this.valueLength = 0;
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
        segmentView.wrap(work, valueOffset, valueLength);
        return segmentView;
    }

    @Override
    public AvroLocation getLocation()
    {
        return location;
    }

    private String decode()
    {
        byte[] dst = new byte[valueLength];
        work.getBytes(valueOffset, dst);
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
            if (p >= workLimit)
            {
                status = READ_UNDERFLOW;
                reading = false;
            }
            else
            {
                int b = work.getByte(p) & 0xff;
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
