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
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_END;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroEncoder;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

public final class AvroEncoderImpl implements AvroEncoder
{
    private final AvroNode root;

    private MutableDirectBuffer buffer;
    private int base;
    private AvroNode[] nodeStack;
    private int[] stateStack;
    private int depth;
    private int limit;

    public AvroEncoderImpl(
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
    public void writeSegment(
        DirectBuffer source,
        int offset,
        int length)
    {
        buffer.putBytes(limit, source, offset, length);
        limit += length;
    }

    private void reset()
    {
        depth = 0;
        limit = base;
        push(root);
    }

    @Override
    public void encode(
        AvroEvent event,
        AvroSource in)
    {
        boolean consumed = false;
        while (!consumed)
        {
            if (depth == 0)
            {
                throw new AvroValidationException("unexpected event after value complete: " + event);
            }

            final int frame = depth - 1;
            final AvroNode node = nodeStack[frame];
            final int state = stateStack[frame];

            switch (node.kind)
            {
            case NULL:
                require(event == AvroEvent.NULL, event);
                pop();
                consumed = true;
                break;
            case BOOLEAN:
                require(event == AvroEvent.BOOLEAN, event);
                buffer.putByte(limit, (byte) (in.getBoolean() ? 1 : 0));
                limit++;
                pop();
                consumed = true;
                break;
            case INT:
                require(event == AvroEvent.INT, event);
                writeVarint(zigzag(in.getInt()));
                pop();
                consumed = true;
                break;
            case LONG:
                require(event == AvroEvent.LONG, event);
                writeVarint(zigzag(in.getLong()));
                pop();
                consumed = true;
                break;
            case FLOAT:
                require(event == AvroEvent.FLOAT, event);
                buffer.putFloat(limit, in.getFloat(), LITTLE_ENDIAN);
                limit += Float.BYTES;
                pop();
                consumed = true;
                break;
            case DOUBLE:
                require(event == AvroEvent.DOUBLE, event);
                buffer.putDouble(limit, in.getDouble(), LITTLE_ENDIAN);
                limit += Double.BYTES;
                pop();
                consumed = true;
                break;
            case BYTES:
                require(event == AvroEvent.BYTES, event);
                writeBytes(in);
                pop();
                consumed = true;
                break;
            case STRING:
                require(event == AvroEvent.STRING, event);
                writeBytes(in);
                pop();
                consumed = true;
                break;
            case FIXED:
                require(event == AvroEvent.FIXED, event);
                buffer.putBytes(limit, in.buffer(), in.offset(), in.length());
                limit += in.length();
                pop();
                consumed = true;
                break;
            case ENUM:
                require(event == AvroEvent.ENUM, event);
                writeVarint(zigzag(in.getInt()));
                pop();
                consumed = true;
                break;
            case RECORD:
                if (state == 0)
                {
                    require(event == AvroEvent.RECORD_START, event);
                    stateStack[frame] = 1;
                    consumed = true;
                }
                else if (state <= node.fieldNames.length)
                {
                    require(event == AvroEvent.FIELD_NAME, event);
                    stateStack[frame] = state + 1;
                    push(node.children[state - 1]);
                    consumed = true;
                }
                else
                {
                    require(event == AvroEvent.RECORD_END, event);
                    pop();
                    consumed = true;
                }
                break;
            case ARRAY:
                if (state == 0)
                {
                    require(event == AvroEvent.ARRAY_START, event);
                    stateStack[frame] = 1;
                    consumed = true;
                }
                else if (event == ARRAY_END)
                {
                    writeVarint(0);
                    pop();
                    consumed = true;
                }
                else
                {
                    writeVarint(zigzag(1));
                    push(node.children[0]);
                }
                break;
            case MAP:
                if (state == 0)
                {
                    require(event == AvroEvent.MAP_START, event);
                    stateStack[frame] = 1;
                    consumed = true;
                }
                else if (event == MAP_END)
                {
                    writeVarint(0);
                    pop();
                    consumed = true;
                }
                else
                {
                    require(event == AvroEvent.MAP_KEY, event);
                    writeVarint(zigzag(1));
                    writeBytes(in);
                    push(node.children[0]);
                    consumed = true;
                }
                break;
            case UNION:
                require(event == AvroEvent.UNION_BRANCH, event);
                int index = in.getInt();
                require(index >= 0 && index < node.children.length, event);
                writeVarint(zigzag(index));
                nodeStack[frame] = node.children[index];
                stateStack[frame] = 0;
                consumed = true;
                break;
            default:
                throw new AvroValidationException("unexpected schema kind: " + node.kind);
            }
        }
    }

    @Override
    public int length()
    {
        return limit - base;
    }

    private void writeBytes(
        AvroSource in)
    {
        int length = in.length();
        writeVarint(zigzag(length));
        buffer.putBytes(limit, in.buffer(), in.offset(), length);
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
        AvroEvent event)
    {
        if (!condition)
        {
            throw new AvroValidationException("event does not match schema: " + event);
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
