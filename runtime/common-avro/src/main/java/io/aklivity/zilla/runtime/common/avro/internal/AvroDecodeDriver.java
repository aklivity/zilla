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

import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ARRAY_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ARRAY_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BOOLEAN;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BYTES;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.CONTINUE_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.DOUBLE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_DOCUMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ENUM;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIXED;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FLOAT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.LONG;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_KEY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.NULL;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.RECORD_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.RECORD_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_DOCUMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_SEGMENT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.UNION_BRANCH;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.PENDING;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;

/**
 * The schema-bound decode driver: walks the compiled schema in lockstep with the buffer, emitting an
 * {@link AvroEvent} stream (framed by {@code START_DOCUMENT}/{@code END_DOCUMENT}) to the bound root
 * {@link AvroSink}, resuming across fragmented {@link #feed} calls. Implements {@link AvroController}:
 * a downstream stage may opt the current datum into verbatim segment delivery, in which case the value
 * is scanned (still validating) without structured emission and delivered as raw segment chunks.
 */
final class AvroDecodeDriver implements AvroController
{
    private static final int READ_OK = 0;
    private static final int READ_UNDERFLOW = 1;
    private static final int READ_MALFORMED = 2;

    private static final int STEP_CONTINUE = 0;
    private static final int STEP_PENDING = 1;
    private static final int STEP_REJECTED = 2;
    private static final int STEP_COMPLETE = 3;

    private enum Phase
    {
        NEW,
        BODY,
        SEGMENT,
        END,
        DONE
    }

    private final AvroNode root;
    private final AvroSink sink;
    private final AvroCursor cursor;

    private final MutableDirectBuffer work;
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

    AvroDecodeDriver(
        AvroNode root,
        AvroSink sink)
    {
        this.root = root;
        this.sink = sink;
        this.cursor = new AvroCursor();
        this.work = new ExpandableArrayBuffer();
        this.nodeStack = new AvroNode[16];
        this.stateStack = new int[16];
        this.countStack = new long[16];
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
        segmentRequested = false;
        segmenting = false;
        segmentStarted = false;
        push(root);
    }

    Status feed(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        work.putBytes(workLimit, buffer, offset, length);
        workLimit += length;
        pos = 0;

        Status status = PENDING;
        boolean running = true;
        while (running)
        {
            switch (phase)
            {
            case NEW:
                int started = emit(START_DOCUMENT);
                if (started == STEP_REJECTED)
                {
                    status = REJECTED;
                    running = false;
                }
                else if (segmentRequested)
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
                    int step = step(nodeStack[depth - 1]);
                    if (step == STEP_PENDING)
                    {
                        status = PENDING;
                        running = false;
                    }
                    else if (step == STEP_REJECTED)
                    {
                        status = REJECTED;
                        running = false;
                    }
                    else if (step == STEP_COMPLETE)
                    {
                        status = COMPLETE;
                        running = false;
                        phase = Phase.DONE;
                    }
                }
                break;
            case SEGMENT:
                int scanStart = pos;
                int scan = STEP_CONTINUE;
                while (scan == STEP_CONTINUE && depth > 0)
                {
                    scan = step(nodeStack[depth - 1]);
                }
                if (scan == STEP_REJECTED)
                {
                    status = REJECTED;
                    running = false;
                }
                else if (depth == 0)
                {
                    status = emitSegmentDone(scanStart);
                    running = false;
                    phase = Phase.DONE;
                }
                else
                {
                    cursor.setSegment(work, scanStart, pos - scanStart);
                    int chunk = emitSegment(segmentStarted ? CONTINUE_SEGMENT : START_SEGMENT);
                    segmentStarted = true;
                    status = chunk == STEP_REJECTED ? REJECTED : PENDING;
                    running = false;
                }
                break;
            case END:
                int ended = emit(END_DOCUMENT);
                status = ended == STEP_REJECTED ? REJECTED : COMPLETE;
                running = false;
                phase = Phase.DONE;
                break;
            default:
                running = false;
                break;
            }
        }

        if (status == PENDING)
        {
            compact();
        }
        else
        {
            workLimit = 0;
        }

        return status;
    }

    private Status emitSegmentDone(
        int scanStart)
    {
        Status status;
        if (!segmentStarted)
        {
            cursor.setSegment(work, scanStart, pos - scanStart);
            int begin = emitSegment(START_SEGMENT);
            segmentStarted = true;
            if (begin == STEP_REJECTED)
            {
                status = REJECTED;
            }
            else
            {
                cursor.setSegment(work, pos, 0);
                int end = emitSegment(END_SEGMENT);
                status = end == STEP_REJECTED ? REJECTED : COMPLETE;
            }
        }
        else
        {
            cursor.setSegment(work, scanStart, pos - scanStart);
            int end = emitSegment(END_SEGMENT);
            status = end == STEP_REJECTED ? REJECTED : COMPLETE;
        }
        return status;
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
        cursor.clear();
        int result = emit(event);
        if (result == STEP_CONTINUE)
        {
            pop();
        }
        return result;
    }

    private int stepBoolean()
    {
        int result;
        if (pos >= workLimit)
        {
            result = STEP_PENDING;
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
                cursor.setBoolean(b != 0);
                result = emit(BOOLEAN);
                if (result == STEP_CONTINUE)
                {
                    pop();
                }
            }
        }
        return result;
    }

    private int stepInteger(
        AvroNode node)
    {
        boolean isInt = node.kind == AvroKind.INT;
        int read = readVarint(pos, isInt ? 5 : 10);
        int result = toStep(read);
        if (read == READ_OK)
        {
            pos = scratchNext;
            long decoded = zigzag(scratchValue);
            if (isInt)
            {
                cursor.setInt((int) decoded);
                result = emit(INT);
            }
            else
            {
                cursor.setLong(decoded);
                result = emit(LONG);
            }
            if (result == STEP_CONTINUE)
            {
                pop();
            }
        }
        return result;
    }

    private int stepFloat()
    {
        int result;
        if (pos + Float.BYTES > workLimit)
        {
            result = STEP_PENDING;
        }
        else
        {
            cursor.setFloat(work.getFloat(pos, LITTLE_ENDIAN));
            pos += Float.BYTES;
            result = emit(FLOAT);
            if (result == STEP_CONTINUE)
            {
                pop();
            }
        }
        return result;
    }

    private int stepDouble()
    {
        int result;
        if (pos + Double.BYTES > workLimit)
        {
            result = STEP_PENDING;
        }
        else
        {
            cursor.setDouble(work.getDouble(pos, LITTLE_ENDIAN));
            pos += Double.BYTES;
            result = emit(DOUBLE);
            if (result == STEP_CONTINUE)
            {
                pop();
            }
        }
        return result;
    }

    private int stepBytes(
        AvroNode node)
    {
        int read = readVarint(pos, 10);
        int result = toStep(read);
        if (read == READ_OK)
        {
            result = readPayload(zigzag(scratchValue), scratchNext);
            if (result == STEP_CONTINUE)
            {
                result = emit(node.kind == AvroKind.STRING ? STRING : BYTES);
                if (result == STEP_CONTINUE)
                {
                    pop();
                }
            }
        }
        return result;
    }

    private int readPayload(
        long length,
        int dataStart)
    {
        int result;
        if (length < 0 || length > Integer.MAX_VALUE)
        {
            result = STEP_REJECTED;
        }
        else if (dataStart + length > workLimit)
        {
            result = STEP_PENDING;
        }
        else
        {
            cursor.setBytes(work, dataStart, (int) length);
            pos = dataStart + (int) length;
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int stepFixed(
        AvroNode node)
    {
        int result;
        if (pos + node.size > workLimit)
        {
            result = STEP_PENDING;
        }
        else
        {
            cursor.setBytes(work, pos, node.size);
            pos += node.size;
            result = emit(FIXED);
            if (result == STEP_CONTINUE)
            {
                pop();
            }
        }
        return result;
    }

    private int stepEnum(
        AvroNode node)
    {
        int read = readVarint(pos, 5);
        int result = toStep(read);
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
                cursor.setEnum((int) index, node.symbols[(int) index]);
                result = emit(ENUM);
                if (result == STEP_CONTINUE)
                {
                    pop();
                }
            }
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
            cursor.clear();
            result = emit(RECORD_START);
            if (result == STEP_CONTINUE)
            {
                stateStack[frame] = 1;
            }
        }
        else if (state <= node.fieldNames.length)
        {
            int index = state - 1;
            cursor.setName(node.fieldNames[index]);
            result = emit(FIELD_NAME);
            if (result == STEP_CONTINUE)
            {
                stateStack[frame] = state + 1;
                push(node.children[index]);
            }
        }
        else
        {
            cursor.clear();
            result = emit(RECORD_END);
            if (result == STEP_CONTINUE)
            {
                pop();
            }
        }
        return result;
    }

    private int stepArray(
        AvroNode node)
    {
        int frame = depth - 1;
        int state = stateStack[frame];
        int result = STEP_CONTINUE;
        if (state == 0)
        {
            cursor.clear();
            result = emit(ARRAY_START);
            if (result == STEP_CONTINUE)
            {
                stateStack[frame] = 1;
            }
        }
        else if (state == 1)
        {
            result = stepBlockHeader(frame, ARRAY_END);
        }
        else if (countStack[frame] > 0)
        {
            countStack[frame]--;
            push(node.children[0]);
        }
        else
        {
            stateStack[frame] = 1;
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
            cursor.clear();
            result = emit(MAP_START);
            if (result == STEP_CONTINUE)
            {
                stateStack[frame] = 1;
            }
        }
        else if (state == 1)
        {
            result = stepBlockHeader(frame, MAP_END);
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
            result = toStep(read);
            if (read == READ_OK)
            {
                result = readPayload(zigzag(scratchValue), scratchNext);
                if (result == STEP_CONTINUE)
                {
                    result = emit(MAP_KEY);
                    if (result == STEP_CONTINUE)
                    {
                        stateStack[frame] = 3;
                    }
                }
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
        int result = toStep(read);
        if (read == READ_OK)
        {
            pos = scratchNext;
            if (scratchValue == 0)
            {
                cursor.clear();
                result = emit(endEvent);
                if (result == STEP_CONTINUE)
                {
                    pop();
                }
            }
            else
            {
                countStack[frame] = scratchValue;
                stateStack[frame] = 2;
            }
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
            result = toStep(read);
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
                    cursor.setInt((int) index);
                    result = emit(UNION_BRANCH);
                    if (result == STEP_CONTINUE)
                    {
                        stateStack[frame] = 1;
                        push(node.children[(int) index]);
                    }
                }
            }
        }
        else
        {
            pop();
            result = STEP_CONTINUE;
        }
        return result;
    }

    private int emit(
        AvroEvent event)
    {
        int result;
        if (segmenting)
        {
            result = STEP_CONTINUE;
        }
        else
        {
            result = mapStatus(sink.feed(this, cursor, event));
        }
        return result;
    }

    private int emitSegment(
        AvroEvent event)
    {
        return mapStatus(sink.feed(this, cursor, event));
    }

    private static int mapStatus(
        Status status)
    {
        return status == REJECTED ? STEP_REJECTED : status == COMPLETE ? STEP_COMPLETE : STEP_CONTINUE;
    }

    private static int toStep(
        int read)
    {
        int step;
        switch (read)
        {
        case READ_UNDERFLOW:
            step = STEP_PENDING;
            break;
        case READ_MALFORMED:
            step = STEP_REJECTED;
            break;
        default:
            step = STEP_CONTINUE;
            break;
        }
        return step;
    }

    private void compact()
    {
        int remaining = workLimit - pos;
        if (pos > 0 && remaining > 0)
        {
            work.putBytes(0, work, pos, remaining);
        }
        workLimit = remaining;
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
