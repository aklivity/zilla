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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Buffer-backed {@link ProtobufGenerator}. Output is one logical byte stream split into flow-control
 * chunks that the consumer concatenates before parsing — so repeated records of the same message merge.
 * <p>
 * {@link #startMessage(int, int)} reserves a length slot sized to an upper bound; the actual length is
 * computed later. When the buffer fills, {@link #flush()} closes each open message record by filling its
 * slot with the body present plus the bytes still deferred by an in-flight {@link #writeSegment}, then the
 * caller drains and re-{@link #wrap(MutableDirectBuffer, int, int) wraps}. The open messages are reopened
 * lazily — re-emitted as fresh records on the next field — so the per-record fragments merge on decode. A
 * value larger than a whole chunk fragments mid-byte via {@link #writeSegment}: its length prefix (the
 * known total) is written once and its body streams as raw continuation bytes across chunks, the enclosing
 * records carrying that total via their deferred count until it is fully written.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private static final int KIND_MESSAGE = 0;
    private static final int KIND_GROUP = 1;

    private final ProtobufWriter writer;

    private int[] fields;
    private int[] kinds;
    private int[] slots;
    private int[] widths;
    private boolean[] committed;
    private int depth;
    private int limit;
    private int segmentRemaining;
    private int consumed;
    private boolean needsReopen;
    private boolean flushed;

    public ProtobufGeneratorImpl()
    {
        this.writer = new ProtobufWriter();
        this.fields = new int[8];
        this.kinds = new int[8];
        this.slots = new int[8];
        this.widths = new int[8];
        this.committed = new boolean[8];
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        if (limit > buffer.capacity() - offset)
        {
            throw new IllegalArgumentException("limit " + limit + " exceeds capacity " +
                (buffer.capacity() - offset));
        }
        this.limit = limit;
        writer.wrap(buffer, offset, offset + limit);
        if (flushed)
        {
            // continuation: the committed levels stay open (reopened lazily on the next field) and any
            // in-flight segment keeps streaming — nothing is re-emitted here
            flushed = false;
        }
        else
        {
            depth = 0;
            segmentRemaining = 0;
            needsReopen = false;
        }
        consumed = 0;
        return this;
    }

    @Override
    public int length()
    {
        return writer.length();
    }

    @Override
    public int consumed()
    {
        return consumed;
    }

    @Override
    public int remaining()
    {
        return limit - writer.length() - reopenCost() - closeCost();
    }

    @Override
    public ProtobufGenerator writeInt32(
        int field,
        int value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeInt64(
        int field,
        long value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt32(
        int field,
        int value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value & 0xffffffffL);
        return this;
    }

    @Override
    public ProtobufGenerator writeUInt64(
        int field,
        long value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt32(
        int field,
        int value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeZigzag32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSInt64(
        int field,
        long value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeZigzag64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed32(
        int field,
        int value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFixed64(
        int field,
        long value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed32(
        int field,
        int value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeSFixed64(
        int field,
        long value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeFloat(
        int field,
        float value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I32);
        writer.writeFixed32(Float.floatToIntBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeDouble(
        int field,
        double value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.I64);
        writer.writeFixed64(Double.doubleToLongBits(value));
        return this;
    }

    @Override
    public ProtobufGenerator writeBool(
        int field,
        boolean value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(value ? 1L : 0L);
        return this;
    }

    @Override
    public ProtobufGenerator writeEnum(
        int field,
        int number)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.VARINT);
        writer.writeVarint64(number);
        return this;
    }

    @Override
    public ProtobufGenerator writeString(
        int field,
        String value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        byte[] value)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value);
        return this;
    }

    @Override
    public ProtobufGenerator writeBytes(
        int field,
        DirectBuffer value,
        int offset,
        int length)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(value, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator writeMessage(
        int field,
        DirectBuffer message,
        int offset,
        int length)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.LEN);
        writer.writeBytes(message, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator writeSegment(
        int field,
        DirectBuffer value,
        int offset,
        int length,
        int deferred)
    {
        if (segmentRemaining == 0)
        {
            int total = length + deferred;
            int header = varintSize((long) field << 3) + varintSize(total);
            if (remaining() < header + 1)
            {
                // not even the tag, length prefix, and one body byte fit; take nothing so the caller drains
                return this;
            }
            reopen();
            writer.writeTag(field, ProtobufWireType.LEN);
            writer.writeVarint32(total);
            segmentRemaining = total;
        }
        int now = Math.min(length, remaining());
        writer.writeRaw(value, offset, now);
        segmentRemaining -= now;
        consumed += now;
        if (segmentRemaining == 0)
        {
            completeSegment();
        }
        return this;
    }

    private void completeSegment()
    {
        // emit the EGROUPs deferred while the value fragmented, innermost first, right after its bytes —
        // the enclosing message lengths already counted them at flush
        for (int level = depth - 1; level >= 0; level--)
        {
            if (kinds[level] == KIND_GROUP && !committed[level])
            {
                writer.writeTag(fields[level], ProtobufWireType.EGROUP);
                committed[level] = true;
                needsReopen = true;
            }
        }
    }

    @Override
    public ProtobufGenerator startMessage(
        int field,
        int length)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.LEN);
        int width = varintSize(length);
        int slot = writer.reserve(width);
        push(field, KIND_MESSAGE, slot, width);
        return this;
    }

    @Override
    public ProtobufGenerator endMessage()
    {
        int level = depth - 1;
        if (kinds[level] != KIND_MESSAGE)
        {
            throw new ProtobufException("open group, expected endGroup");
        }
        if (!committed[level])
        {
            fillMessage(level);
        }
        depth--;
        return this;
    }

    @Override
    public ProtobufGenerator startGroup(
        int field)
    {
        reopen();
        writer.writeTag(field, ProtobufWireType.SGROUP);
        push(field, KIND_GROUP, 0, 0);
        return this;
    }

    @Override
    public ProtobufGenerator endGroup()
    {
        int level = depth - 1;
        if (kinds[level] != KIND_GROUP)
        {
            throw new ProtobufException("open message, expected endMessage");
        }
        if (!committed[level])
        {
            writer.writeTag(fields[level], ProtobufWireType.EGROUP);
        }
        depth--;
        return this;
    }

    @Override
    public ProtobufGenerator writeRaw(
        DirectBuffer source,
        int offset,
        int length)
    {
        reopen();
        writer.writeRaw(source, offset, length);
        return this;
    }

    @Override
    public ProtobufGenerator writeValue(
        int field,
        ProtobufWireType wireType,
        DirectBuffer value,
        int offset,
        int length)
    {
        reopen();
        switch (wireType)
        {
        case LEN:
            writer.writeTag(field, ProtobufWireType.LEN);
            writer.writeBytes(value, offset, length);
            break;
        case SGROUP:
            writer.writeTag(field, ProtobufWireType.SGROUP);
            writer.writeRaw(value, offset, length);
            writer.writeTag(field, ProtobufWireType.EGROUP);
            break;
        default:
            writer.writeTag(field, wireType);
            writer.writeRaw(value, offset, length);
            break;
        }
        return this;
    }

    @Override
    public ProtobufGenerator flush()
    {
        // a record closes at the value being fragmented, so each message length counts the deferred value
        // bytes plus the end-tags of the open groups inside it (those EGROUPs are emitted when the value
        // completes; a group with no in-flight value is end-tagged here and closes its own record)
        int trailing = segmentRemaining;
        for (int level = depth - 1; level >= 0; level--)
        {
            if (committed[level])
            {
                continue;
            }
            if (kinds[level] == KIND_GROUP)
            {
                if (segmentRemaining == 0)
                {
                    writer.writeTag(fields[level], ProtobufWireType.EGROUP);
                    committed[level] = true;
                }
                else
                {
                    trailing += varintSize((long) fields[level] << 3);
                }
            }
            else
            {
                int body = writer.offset() - (slots[level] + widths[level]) + trailing;
                if (varintSize(body) > widths[level])
                {
                    throw new ProtobufException("nested message body " + body +
                        " exceeds reserved length width " + widths[level]);
                }
                writer.putPaddedVarint(slots[level], body, widths[level]);
                committed[level] = true;
            }
        }
        needsReopen = depth > 0;
        flushed = true;
        return this;
    }

    private void reopen()
    {
        if (needsReopen && segmentRemaining == 0)
        {
            for (int level = 0; level < depth; level++)
            {
                if (committed[level])
                {
                    if (kinds[level] == KIND_MESSAGE)
                    {
                        writer.writeTag(fields[level], ProtobufWireType.LEN);
                        slots[level] = writer.reserve(widths[level]);
                    }
                    else
                    {
                        writer.writeTag(fields[level], ProtobufWireType.SGROUP);
                    }
                    committed[level] = false;
                }
            }
            needsReopen = false;
        }
    }

    private void fillMessage(
        int level)
    {
        int bodyLength = writer.offset() - (slots[level] + widths[level]);
        if (varintSize(bodyLength) > widths[level])
        {
            throw new ProtobufException("nested message body " + bodyLength +
                " exceeds reserved length width " + widths[level]);
        }
        writer.putPaddedVarint(slots[level], bodyLength, widths[level]);
    }

    private int reopenCost()
    {
        int cost = 0;
        if (needsReopen && segmentRemaining == 0)
        {
            for (int level = 0; level < depth; level++)
            {
                if (committed[level])
                {
                    cost += kinds[level] == KIND_MESSAGE
                        ? varintSize((long) fields[level] << 3) + widths[level]
                        : varintSize((long) fields[level] << 3);
                }
            }
        }
        return cost;
    }

    // bytes held free for the end-tags of open groups not yet closed, so a flush or the value's completion
    // can always emit them within the limit
    private int closeCost()
    {
        int cost = 0;
        for (int level = 0; level < depth; level++)
        {
            if (kinds[level] == KIND_GROUP && !committed[level])
            {
                cost += varintSize((long) fields[level] << 3);
            }
        }
        return cost;
    }

    private void push(
        int field,
        int kind,
        int slot,
        int width)
    {
        if (depth >= fields.length)
        {
            fields = Arrays.copyOf(fields, fields.length * 2);
            kinds = Arrays.copyOf(kinds, kinds.length * 2);
            slots = Arrays.copyOf(slots, slots.length * 2);
            widths = Arrays.copyOf(widths, widths.length * 2);
            committed = Arrays.copyOf(committed, committed.length * 2);
        }
        fields[depth] = field;
        kinds[depth] = kind;
        slots[depth] = slot;
        widths[depth] = width;
        committed[depth] = false;
        depth++;
    }

    private static int varintSize(
        long value)
    {
        long remaining = value & 0xffffffffL;
        int size = 1;
        while (remaining >= 0x80L)
        {
            remaining >>>= 7;
            size++;
        }
        return size;
    }
}
