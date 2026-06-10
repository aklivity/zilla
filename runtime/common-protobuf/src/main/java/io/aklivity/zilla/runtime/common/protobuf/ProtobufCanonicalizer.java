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
package io.aklivity.zilla.runtime.common.protobuf;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufReader;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufWriter;

/**
 * Re-serializes a fully-buffered Protobuf message against a {@link ProtobufSchema} into a canonical
 * wire encoding: known fields ascending by number, scalars minimally re-encoded, repeated scalars
 * packed, nested messages length-delimited, proto2 groups delimited, and map entries in encounter
 * order. Two distinct valid encodings of the same logical message canonicalize to identical bytes,
 * which is the comparison a binary round-trip conformance check needs.
 * <p>
 * This is format-neutral — it touches no JSON. Unknown (non-descriptor) fields are dropped; cases
 * that require unknown-field retention belong on the conformance failure list until supported.
 */
public final class ProtobufCanonicalizer
{
    private final ProtobufSchema schema;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufWriter> writers;

    ProtobufCanonicalizer(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.scratch = new ArrayList<>();
        this.writers = new ArrayList<>();
    }

    public int canonicalize(
        String messageName,
        DirectBuffer in,
        int offset,
        int length,
        MutableDirectBuffer out,
        int outOffset)
    {
        ProtobufMessage message = schema.message(messageName);
        if (message == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        ProtobufWriter writer = new ProtobufWriter().wrap(out, outOffset);
        writeBody(message, in, offset, length, writer, 0);
        return writer.length();
    }

    private void writeBody(
        ProtobufMessage message,
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        for (ProtobufField field : message.sortedFields())
        {
            if (field.repeated() && isMap(field))
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

    private void writeSingular(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        int valueOffset = -1;
        int valueLength = 0;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == field.number())
            {
                int[] region = readRegion(field, reader, number, wireType);
                valueOffset = region[0];
                valueLength = region[1];
            }
            else
            {
                reader.skipField(number, wireType);
            }
        }

        if (valueOffset >= 0)
        {
            writeValue(field, buffer, valueOffset, valueLength, writer, depth);
        }
    }

    private void writeRepeated(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        if (field.type().packable())
        {
            ProtobufWriter block = acquire(depth).wrap(scratch(depth), 0);
            ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
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
            ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
            while (reader.hasRemaining())
            {
                int tag = reader.readVarint32();
                int number = tag >>> 3;
                ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
                if (number == field.number())
                {
                    int[] region = readRegion(field, reader, number, wireType);
                    writeValue(field, buffer, region[0], region[1], writer, depth);
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
        DirectBuffer buffer,
        int offset,
        int length,
        ProtobufWriter writer,
        int depth)
    {
        ProtobufMessage entry = schema.resolveMessage(field);
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
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
        DirectBuffer buffer,
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
            ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
            copyScalar(field, reader, writer);
        }
    }

    private int[] readRegion(
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
        return new int[]{regionOffset, regionLength};
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

    private ExpandableArrayBuffer scratch(
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
            scratch.add(new ExpandableArrayBuffer());
            writers.add(new ProtobufWriter());
        }
        return writers.get(depth);
    }
}
