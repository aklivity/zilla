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
import java.util.Arrays;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Re-serializes a fully-buffered Protobuf message against a {@link ProtobufSchema} into a canonical
 * wire encoding: known fields ascending by number, scalars minimally re-encoded, repeated scalars
 * packed, nested messages length-delimited, proto2 groups delimited, and map entries in encounter
 * order. Two distinct valid encodings of the same logical message canonicalize to identical bytes,
 * which is the comparison a binary round-trip conformance check needs.
 * <p>
 * This is format-neutral — it touches no JSON. Unknown (non-descriptor) fields are retained: each
 * is passed through verbatim (tag and value, groups included) and merged into the ascending
 * field-number ordering alongside known fields, so binary round-trip retention cases hold.
 * <p>
 * Reuse a single instance per worker thread: scan readers, scratch buffers, and the field-number
 * working set are pooled by message-nesting depth, so after warmup canonicalize allocates nothing
 * on the hot path.
 */
public final class ProtobufCanonicalizer
{
    private final ProtobufSchema schema;
    private final ProtobufWriter rootWriter;
    private final ProtobufReader scalarReader;
    private final List<ExpandableArrayBufferEx> scratch;
    private final List<ProtobufWriter> writers;
    private final List<ProtobufReader> readers;
    private final List<int[]> numberBuffers;

    public ProtobufCanonicalizer(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.rootWriter = new ProtobufWriter();
        this.scalarReader = new ProtobufReader();
        this.scratch = new ArrayList<>();
        this.writers = new ArrayList<>();
        this.readers = new ArrayList<>();
        this.numberBuffers = new ArrayList<>();
    }

    public int canonicalize(
        String messageName,
        DirectBufferEx in,
        int offset,
        int length,
        MutableDirectBufferEx out,
        int outOffset)
    {
        ProtobufMessage message = schema.message(messageName);
        if (message == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        ProtobufWriter writer = rootWriter.wrap(out, outOffset);
        writeBody(message, in, offset, length, writer, 0);
        return writer.length();
    }

    private void writeBody(
        ProtobufMessage message,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        int count = collectNumbers(buffer, offset, length, depth);
        int[] numbers = numbers(depth);
        int previous = -1;
        for (int i = 0; i < count; i++)
        {
            int number = numbers[i];
            if (number == previous)
            {
                continue;
            }
            previous = number;

            ProtobufField field = message.field(number);
            if (field == null)
            {
                writeUnknown(number, buffer, offset, length, writer, depth);
            }
            else if (field.repeated() && isMap(field))
            {
                writeMap(field, buffer, offset, length, writer, depth);
            }
            else if (field.repeated())
            {
                writeRepeated(field, buffer, offset, length, writer, depth);
            }
            else
            {
                writeSingular(field, buffer, offset, length, writer, depth);
            }
        }
    }

    private int collectNumbers(
        DirectBufferEx buffer,
        int offset,
        int length,
        int depth)
    {
        ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
        int count = 0;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            int[] target = ensureNumbers(depth, count + 1);
            target[count++] = number;
            reader.skipField(number, wireType);
        }
        Arrays.sort(numbers(depth), 0, count);
        return count;
    }

    private void writeUnknown(
        int number,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
        while (reader.hasRemaining())
        {
            int tagOffset = reader.offset();
            int tag = reader.readVarint32();
            int fieldNumber = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (fieldNumber == number)
            {
                if (wireType == ProtobufWireType.SGROUP)
                {
                    reader.skipGroup(number);
                }
                else if (wireType == ProtobufWireType.LEN)
                {
                    reader.skip(reader.readLength());
                }
                else
                {
                    reader.skipField(wireType);
                }
                writer.writeRaw(buffer, tagOffset, reader.offset() - tagOffset);
            }
            else
            {
                reader.skipField(fieldNumber, wireType);
            }
        }
    }

    private void writeSingular(
        ProtobufField field,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
        long region = -1;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == field.number())
            {
                region = readRegion(field, reader, number, wireType);
            }
            else
            {
                reader.skipField(number, wireType);
            }
        }

        if (region >= 0)
        {
            writeValue(field, buffer, (int) (region >>> 32), (int) region, writer, depth);
        }
    }

    private void writeRepeated(
        ProtobufField field,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        if (field.type().packable())
        {
            ProtobufWriter block = acquire(depth).wrap(scratch(depth), 0);
            ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
            while (reader.hasRemaining())
            {
                int tag = reader.readVarint32();
                int number = tag >>> 3;
                ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
                if (number == field.number())
                {
                    if (wireType == ProtobufWireType.LEN)
                    {
                        int blockLength = reader.readLength();
                        int blockLimit = reader.offset() + blockLength;
                        while (reader.offset() < blockLimit)
                        {
                            copyScalar(field, reader, block);
                        }
                    }
                    else
                    {
                        copyScalar(field, reader, block);
                    }
                }
                else
                {
                    reader.skipField(number, wireType);
                }
            }
            if (block.length() > 0)
            {
                writer.writeTag(field.number(), ProtobufWireType.LEN);
                writer.writeBytes(scratch(depth), 0, block.length());
            }
        }
        else
        {
            ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
            while (reader.hasRemaining())
            {
                int tag = reader.readVarint32();
                int number = tag >>> 3;
                ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
                if (number == field.number())
                {
                    long region = readRegion(field, reader, number, wireType);
                    writeValue(field, buffer, (int) (region >>> 32), (int) region, writer, depth);
                }
                else
                {
                    reader.skipField(number, wireType);
                }
            }
        }
    }

    private void writeMap(
        ProtobufField field,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        ProtobufMessage entry = schema.resolveMessage(field);
        ProtobufReader reader = reader(depth).wrap(buffer, offset, offset + length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == field.number() && wireType == ProtobufWireType.LEN)
            {
                int entryLength = reader.readLength();
                int entryOffset = reader.offset();
                reader.skip(entryLength);

                ProtobufWriter body = acquire(depth).wrap(scratch(depth), 0);
                writeBody(entry, buffer, entryOffset, entryLength, body, depth + 1);
                writer.writeTag(field.number(), ProtobufWireType.LEN);
                writer.writeBytes(scratch(depth), 0, body.length());
            }
            else
            {
                reader.skipField(number, wireType);
            }
        }
    }

    private void writeValue(
        ProtobufField field,
        DirectBufferEx buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        if (field.type() == ProtobufType.GROUP)
        {
            ProtobufMessage group = schema.resolveMessage(field);
            writer.writeTag(field.number(), ProtobufWireType.SGROUP);
            writeBody(group, buffer, offset, length, writer, depth + 1);
            writer.writeTag(field.number(), ProtobufWireType.EGROUP);
        }
        else if (field.composite())
        {
            ProtobufMessage message = schema.resolveMessage(field);
            ProtobufWriter body = acquire(depth).wrap(scratch(depth), 0);
            writeBody(message, buffer, offset, length, body, depth + 1);
            writer.writeTag(field.number(), ProtobufWireType.LEN);
            writer.writeBytes(scratch(depth), 0, body.length());
        }
        else
        {
            writer.writeTag(field.number(), field.type().wireType());
            copyScalar(field, scalarReader.wrap(buffer, offset, offset + length), writer);
        }
    }

    private long readRegion(
        ProtobufField field,
        ProtobufReader reader,
        int number,
        ProtobufWireType wireType)
    {
        int regionOffset;
        int regionLength;
        if (field.type() == ProtobufType.GROUP)
        {
            regionOffset = reader.offset();
            regionLength = reader.skipGroup(number) - regionOffset;
        }
        else if (field.composite())
        {
            regionLength = reader.readLength();
            regionOffset = reader.offset();
            reader.skip(regionLength);
        }
        else
        {
            regionOffset = reader.offset();
            if (wireType == ProtobufWireType.LEN)
            {
                reader.skip(reader.readLength());
            }
            else
            {
                reader.skipField(wireType);
            }
            regionLength = reader.offset() - regionOffset;
        }
        return (long) regionOffset << 32 | regionLength & 0xffffffffL;
    }

    private void copyScalar(
        ProtobufField field,
        ProtobufReader reader,
        ProtobufWriter writer)
    {
        switch (field.type())
        {
        case INT32:
        case INT64:
        case UINT64:
        case ENUM:
            writer.writeVarint64(reader.readVarint64());
            break;
        case UINT32:
            writer.writeVarint64(reader.readVarint32() & 0xffffffffL);
            break;
        case SINT32:
            writer.writeZigzag32(reader.readZigzag32());
            break;
        case SINT64:
            writer.writeZigzag64(reader.readZigzag64());
            break;
        case BOOL:
            writer.writeVarint64(reader.readVarint64() != 0L ? 1L : 0L);
            break;
        case FIXED32:
        case SFIXED32:
        case FLOAT:
            writer.writeFixed32(reader.readFixed32());
            break;
        case FIXED64:
        case SFIXED64:
        case DOUBLE:
            writer.writeFixed64(reader.readFixed64());
            break;
        case STRING:
        case BYTES:
            int len = reader.readLength();
            writer.writeBytes(reader.buffer(), reader.offset(), len);
            reader.skip(len);
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private boolean isMap(
        ProtobufField field)
    {
        ProtobufMessage message = field.composite() && field.typeName() != null
            ? schema.message(field.typeName()) : null;
        return message != null && message.mapEntry();
    }

    private ExpandableArrayBufferEx scratch(
        int depth)
    {
        acquire(depth);
        return scratch.get(depth);
    }

    private ProtobufWriter acquire(
        int depth)
    {
        while (scratch.size() <= depth)
        {
            scratch.add(new ExpandableArrayBufferEx());
            writers.add(new ProtobufWriter());
        }
        return writers.get(depth);
    }

    private ProtobufReader reader(
        int depth)
    {
        while (readers.size() <= depth)
        {
            readers.add(new ProtobufReader());
        }
        return readers.get(depth);
    }

    private int[] numbers(
        int depth)
    {
        while (numberBuffers.size() <= depth)
        {
            numberBuffers.add(new int[16]);
        }
        return numberBuffers.get(depth);
    }

    private int[] ensureNumbers(
        int depth,
        int capacity)
    {
        int[] current = numbers(depth);
        if (current.length < capacity)
        {
            int[] grown = new int[Math.max(capacity, current.length * 2)];
            System.arraycopy(current, 0, grown, 0, current.length);
            numberBuffers.set(depth, grown);
            current = grown;
        }
        return current;
    }
}
