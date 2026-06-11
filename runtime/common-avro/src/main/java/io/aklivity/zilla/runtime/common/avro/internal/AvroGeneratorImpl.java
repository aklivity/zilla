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

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

public final class AvroGeneratorImpl implements AvroGenerator
{
    private final AvroNode root;

    private MutableDirectBuffer buffer;
    private int base;
    private AvroNode[] nodeStack;
    private int[] stateStack;
    private int depth;
    private int limit;

    public AvroGeneratorImpl(
        AvroNode root,
        MutableDirectBuffer buffer,
        int offset)
    {
        this.root = root;
        this.buffer = buffer;
        this.base = offset;
        this.nodeStack = new AvroNode[16];
        this.stateStack = new int[16];
        reset();
    }

    @Override
    public void wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        this.buffer = buffer;
        this.base = offset;
        reset();
    }

    @Override
    public int length()
    {
        return limit - base;
    }

    @Override
    public void writeStartRecord()
    {
        beginValue();
        expect(AvroKind.RECORD);
        require(stateStack[depth - 1] == 0, "unexpected record start");
        stateStack[depth - 1] = 1;
    }

    @Override
    public void writeStartArray()
    {
        beginValue();
        expect(AvroKind.ARRAY);
        require(stateStack[depth - 1] == 0, "unexpected array start");
        stateStack[depth - 1] = 1;
    }

    @Override
    public void writeStartMap()
    {
        beginValue();
        expect(AvroKind.MAP);
        require(stateStack[depth - 1] == 0, "unexpected map start");
        stateStack[depth - 1] = 1;
    }

    @Override
    public void writeEnd()
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
    }

    @Override
    public void writeKey(
        DirectBuffer buffer,
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
    public void writeIndex(
        int index)
    {
        beginValue();
        AvroNode node = expect(AvroKind.UNION);
        require(index >= 0 && index < node.children.length, "union branch out of range");
        writeVarint(zigzag(index));
        nodeStack[depth - 1] = node.children[index];
        stateStack[depth - 1] = 0;
    }

    @Override
    public void writeNull()
    {
        beginValue();
        expect(AvroKind.NULL);
        pop();
    }

    @Override
    public void writeBoolean(
        boolean value)
    {
        beginValue();
        expect(AvroKind.BOOLEAN);
        buffer.putByte(limit, (byte) (value ? 1 : 0));
        limit++;
        pop();
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
        buffer.putFloat(limit, value, LITTLE_ENDIAN);
        limit += Float.BYTES;
        pop();
    }

    @Override
    public void writeDouble(
        double value)
    {
        beginValue();
        expect(AvroKind.DOUBLE);
        buffer.putDouble(limit, value, LITTLE_ENDIAN);
        limit += Double.BYTES;
        pop();
    }

    @Override
    public void writeString(
        DirectBuffer buffer,
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
        DirectBuffer buffer,
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
        DirectBuffer buffer,
        int offset,
        int length)
    {
        beginValue();
        expect(AvroKind.FIXED);
        this.buffer.putBytes(limit, buffer, offset, length);
        limit += length;
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
        DirectBuffer buffer,
        int offset,
        int length)
    {
        this.buffer.putBytes(limit, buffer, offset, length);
        limit += length;
    }

    private void reset()
    {
        depth = 0;
        limit = base;
        push(root);
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
        require(node.kind == kind, "expected " + node.kind + " but was " + kind);
        return node;
    }

    private void writeLengthPrefixed(
        DirectBuffer source,
        int offset,
        int length)
    {
        writeVarint(zigzag(length));
        buffer.putBytes(limit, source, offset, length);
        limit += length;
    }

    private void writeVarint(
        long value)
    {
        long u = value;
        while ((u & ~0x7fL) != 0)
        {
            buffer.putByte(limit, (byte) ((u & 0x7f) | 0x80));
            limit++;
            u >>>= 7;
        }
        buffer.putByte(limit, (byte) (u & 0x7f));
        limit++;
    }

    private void require(
        boolean condition,
        String message)
    {
        if (!condition)
        {
            throw new AvroValidationException(message);
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
