/*
 * Copyright 2021-2026 Aklivity Inc
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

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroParsingException;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;

public final class AvroGeneratorImpl implements AvroGenerator
{
    // shared empty target so an unwrapped generator is in a defensible state (length/remaining read zero)
    // before transform re-targets it at the caller's destination, rather than holding a null buffer
    private static final MutableDirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    private final AvroNode root;

    private MutableDirectBufferEx buffer = EMPTY;
    private int base;
    private int bound;
    private AvroNode[] nodeStack;
    private int[] stateStack;
    private int depth;
    private int progress;
    private int segmentRemaining;

    public AvroGeneratorImpl(
        AvroSchema schema)
    {
        this.root = (AvroNode) schema.type();
        this.nodeStack = new AvroNode[16];
        this.stateStack = new int[16];
        this.depth = 0;
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    @Override
    public AvroGenerator wrap(
        MutableDirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (limit > buffer.capacity())
        {
            throw new IllegalArgumentException("limit exceeds buffer capacity");
        }
        this.buffer = buffer;
        this.base = offset;
        this.bound = limit;
        this.progress = offset;
        // depth > 0 means a datum is mid-flight (a resume after a bounded-output drain): keep the
        // schema-walk stack so writing continues from the current field. Avro is unframed, so the
        // drained bytes are a valid prefix and no level needs reopening — only the buffer is retargeted.
        if (depth == 0)
        {
            push(root);
        }
        return this;
    }

    @Override
    public int length()
    {
        return progress - base;
    }

    @Override
    public int remaining()
    {
        return bound - progress;
    }

    @Override
    public boolean writeStartRecord()
    {
        beginValue();
        expect(AvroKind.RECORD);
        require(stateStack[depth - 1] == 0, "unexpected record start");
        stateStack[depth - 1] = 1;
        return true;
    }

    @Override
    public boolean writeStartArray()
    {
        beginValue();
        expect(AvroKind.ARRAY);
        require(stateStack[depth - 1] == 0, "unexpected array start");
        stateStack[depth - 1] = 1;
        return true;
    }

    @Override
    public boolean writeStartMap()
    {
        beginValue();
        expect(AvroKind.MAP);
        require(stateStack[depth - 1] == 0, "unexpected map start");
        stateStack[depth - 1] = 1;
        return true;
    }

    @Override
    public boolean writeEnd()
    {
        AvroNode node = nodeStack[depth - 1];
        switch (node.kind)
        {
        case RECORD:
            require(stateStack[depth - 1] == node.fieldNames.length + 1, "unexpected record end");
            break;
        case ARRAY:
            require(stateStack[depth - 1] == 1, "unexpected array end");
            writeVarint(0);
            break;
        case MAP:
            require(stateStack[depth - 1] == 1, "unexpected map end");
            writeVarint(0);
            break;
        default:
            require(false, "unexpected end");
            break;
        }
        pop();
        return true;
    }

    @Override
    public void writeKey(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        AvroNode node = nodeStack[depth - 1];
        require(node.kind == AvroKind.MAP && stateStack[depth - 1] == 1, "unexpected map key");
        writeVarint(zigzag(1));
        writeLengthPrefixed(buffer, offset, length);
        push(node.children[0]);
    }

    @Override
    public boolean writeIndex(
        int index)
    {
        beginValue();
        AvroNode node = expect(AvroKind.UNION);
        require(index >= 0 && index < node.children.length, "union branch out of range");
        writeVarint(zigzag(index));
        nodeStack[depth - 1] = node.children[index];
        stateStack[depth - 1] = 0;
        return true;
    }

    @Override
    public boolean writeNull()
    {
        beginValue();
        expect(AvroKind.NULL);
        pop();
        return true;
    }

    @Override
    public boolean writeBoolean(
        boolean value)
    {
        beginValue();
        expect(AvroKind.BOOLEAN);
        claimAvailable(1);
        buffer.putByte(progress, (byte) (value ? 1 : 0));
        progress++;
        pop();
        return true;
    }

    @Override
    public void writeInt(
        int value)
    {
        beginValue();
        expect(AvroKind.INT);
        writeVarint(zigzag(value));
        pop();
    }

    @Override
    public void writeLong(
        long value)
    {
        beginValue();
        expect(AvroKind.LONG);
        writeVarint(zigzag(value));
        pop();
    }

    @Override
    public void writeFloat(
        float value)
    {
        beginValue();
        expect(AvroKind.FLOAT);
        claimAvailable(Float.BYTES);
        buffer.putFloat(progress, value, LITTLE_ENDIAN);
        progress += Float.BYTES;
        pop();
    }

    @Override
    public void writeDouble(
        double value)
    {
        beginValue();
        expect(AvroKind.DOUBLE);
        claimAvailable(Double.BYTES);
        buffer.putDouble(progress, value, LITTLE_ENDIAN);
        progress += Double.BYTES;
        pop();
    }

    @Override
    public void writeString(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        beginValue();
        expect(AvroKind.STRING);
        writeLengthPrefixed(buffer, offset, length);
        pop();
    }

    @Override
    public void writeBytes(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        beginValue();
        expect(AvroKind.BYTES);
        writeLengthPrefixed(buffer, offset, length);
        pop();
    }

    @Override
    public void writeFixed(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        beginValue();
        expect(AvroKind.FIXED);
        claimAvailable(length);
        this.buffer.putBytes(progress, buffer, offset, length);
        progress += length;
        pop();
    }

    @Override
    public void writeEnum(
        int index)
    {
        beginValue();
        expect(AvroKind.ENUM);
        writeVarint(zigzag(index));
        pop();
    }

    @Override
    public void writeRaw(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        claimAvailable(length);
        this.buffer.putBytes(progress, buffer, offset, length);
        progress += length;
    }

    @Override
    public int writeSegment(
        DirectBufferEx source,
        int offset,
        int length,
        int deferred)
    {
        if (segmentRemaining == 0)
        {
            beginValue();
            AvroNode node = nodeStack[depth - 1];
            require(node.kind == AvroKind.STRING || node.kind == AvroKind.BYTES || node.kind == AvroKind.FIXED,
                "unexpected segment");
            if (node.kind != AvroKind.FIXED)
            {
                writeVarint(zigzag(length + deferred));
            }
            segmentRemaining = length + deferred;
        }
        int count = Math.min(length, bound - progress);
        if (count > 0)
        {
            buffer.putBytes(progress, source, offset, count);
            progress += count;
            segmentRemaining -= count;
        }
        return count;
    }

    @Override
    public void flush()
    {
        require(segmentRemaining == 0, "incomplete segment");
        pop();
    }

    private void beginValue()
    {
        require(depth > 0, "unexpected value after message complete");
        boolean resolved = false;
        while (!resolved)
        {
            int frame = depth - 1;
            AvroNode node = nodeStack[frame];
            if (node.kind == AvroKind.ARRAY && stateStack[frame] == 1)
            {
                writeVarint(zigzag(1));
                push(node.children[0]);
            }
            else if (node.kind == AvroKind.RECORD && stateStack[frame] >= 1 && stateStack[frame] <= node.fieldNames.length)
            {
                int index = stateStack[frame] - 1;
                stateStack[frame]++;
                push(node.children[index]);
            }
            else
            {
                resolved = true;
            }
        }
    }

    private AvroNode expect(
        AvroKind kind)
    {
        AvroNode node = nodeStack[depth - 1];
        if (node.kind != kind)
        {
            throw new AvroParsingException("expected " + node.kind + " but was " + kind);
        }
        return node;
    }

    private void writeLengthPrefixed(
        DirectBufferEx source,
        int offset,
        int length)
    {
        writeVarint(zigzag(length));
        claimAvailable(length);
        buffer.putBytes(progress, source, offset, length);
        progress += length;
    }

    private void writeVarint(
        long value)
    {
        long u = value;
        while ((u & ~0x7fL) != 0)
        {
            claimAvailable(1);
            buffer.putByte(progress, (byte) ((u & 0x7f) | 0x80));
            progress++;
            u >>>= 7;
        }
        claimAvailable(1);
        buffer.putByte(progress, (byte) (u & 0x7f));
        progress++;
    }

    private void claimAvailable(
        int count)
    {
        if (progress + count > bound)
        {
            throw new AvroParsingException("output exceeds limit");
        }
    }

    private void require(
        boolean condition,
        String message)
    {
        if (!condition)
        {
            throw new AvroParsingException(message);
        }
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
        System.arraycopy(nodeStack, 0, nodes, 0, depth);
        System.arraycopy(stateStack, 0, states, 0, depth);
        nodeStack = nodes;
        stateStack = states;
    }

    private static long zigzag(
        long value)
    {
        return (value << 1) ^ (value >> 63);
    }
}
