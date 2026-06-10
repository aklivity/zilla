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

import java.util.Base64;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Decodes a fully-buffered Protobuf message against a {@link ProtobufSchema} and emits its proto3
 * JSON mapping into a {@link JsonGeneratorEx}.
 * <p>
 * Protobuf fields may arrive in any order and a repeated field's elements may be interleaved with
 * other fields. To produce JSON, where each member appears once with its array elements
 * contiguous, the decoder emits fields in descriptor declaration order, re-scanning the bounded
 * message region once per field. The bound is the size of the single message handed in; the
 * decoder never buffers beyond it.
 */
public final class ProtobufDecoder
{
    private final ProtobufSchema schema;
    private final Base64.Encoder base64;

    public ProtobufDecoder(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.base64 = Base64.getEncoder();
    }

    public void decode(
        String messageName,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufMessage message = schema.message(messageName);
        if (message == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        emitMessage(message, buffer, offset, length, out);
    }

    private void emitMessage(
        ProtobufMessage message,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        if (!emitWellKnown(message.name(), buffer, offset, length, out))
        {
            out.writeStartObject();
            for (ProtobufField field : message.fields())
            {
                emitField(field, buffer, offset, length, out);
            }
            out.writeEnd();
        }
    }

    private void emitField(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        if (field.repeated() && isMap(field))
        {
            emitMap(field, buffer, offset, length, out);
        }
        else if (field.repeated())
        {
            emitRepeated(field, buffer, offset, length, out);
        }
        else
        {
            emitSingular(field, buffer, offset, length, out);
        }
    }

    private void emitSingular(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
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
                requireWireType(field, wireType, false);
                if (field.composite())
                {
                    valueLength = reader.readLength();
                    valueOffset = reader.offset();
                    reader.skip(valueLength);
                }
                else
                {
                    valueOffset = reader.offset();
                    skipScalar(reader, wireType);
                    valueLength = reader.offset() - valueOffset;
                }
            }
            else
            {
                reader.skipField(wireType);
            }
        }

        if (valueOffset >= 0)
        {
            out.writeKey(field.jsonName());
            emitValue(field, buffer, valueOffset, valueLength, out);
        }
    }

    private void skipScalar(
        ProtobufReader reader,
        ProtobufWireType wireType)
    {
        if (wireType == ProtobufWireType.LEN)
        {
            reader.skip(reader.readLength());
        }
        else
        {
            reader.skipField(wireType);
        }
    }

    private void emitRepeated(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        boolean started = false;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == field.number())
            {
                if (!started)
                {
                    out.writeKey(field.jsonName());
                    out.writeStartArray();
                    started = true;
                }

                if (field.type().packable() && wireType == ProtobufWireType.LEN)
                {
                    int blockLength = reader.readLength();
                    int blockLimit = reader.offset() + blockLength;
                    while (reader.offset() < blockLimit)
                    {
                        emitScalar(field, reader, out);
                    }
                }
                else
                {
                    requireWireType(field, wireType, true);
                    int elementOffset;
                    int elementLength;
                    if (field.composite())
                    {
                        elementLength = reader.readLength();
                        elementOffset = reader.offset();
                        reader.skip(elementLength);
                    }
                    else
                    {
                        elementOffset = reader.offset();
                        skipScalar(reader, wireType);
                        elementLength = reader.offset() - elementOffset;
                    }
                    emitValue(field, buffer, elementOffset, elementLength, out);
                }
            }
            else
            {
                reader.skipField(wireType);
            }
        }

        if (started)
        {
            out.writeEnd();
        }
    }

    private void emitMap(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufMessage entry = schema.resolveMessage(field);
        ProtobufField keyField = entry.mapKey();
        ProtobufField valueField = entry.mapValue();
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        boolean started = false;
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

                if (!started)
                {
                    out.writeKey(field.jsonName());
                    out.writeStartObject();
                    started = true;
                }

                emitMapEntry(keyField, valueField, buffer, entryOffset, entryLength, out);
            }
            else
            {
                reader.skipField(wireType);
            }
        }

        if (started)
        {
            out.writeEnd();
        }
    }

    private void emitMapEntry(
        ProtobufField keyField,
        ProtobufField valueField,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        String key = mapKeyDefault(keyField);
        int valueOffset = -1;
        int valueLength = 0;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1)
            {
                key = mapKey(keyField, reader, wireType);
            }
            else if (number == 2)
            {
                if (valueField.composite())
                {
                    valueLength = reader.readLength();
                    valueOffset = reader.offset();
                    reader.skip(valueLength);
                }
                else
                {
                    valueOffset = reader.offset();
                    skipScalar(reader, wireType);
                    valueLength = reader.offset() - valueOffset;
                }
            }
            else
            {
                reader.skipField(wireType);
            }
        }

        out.writeKey(key);
        if (valueOffset >= 0)
        {
            emitValue(valueField, buffer, valueOffset, valueLength, out);
        }
        else
        {
            emitDefault(valueField, out);
        }
    }

    private void emitValue(
        ProtobufField field,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        if (field.composite())
        {
            if (!emitWellKnownValue(field.typeName(), buffer, offset, length, out))
            {
                ProtobufMessage message = schema.resolveMessage(field);
                emitMessage(message, buffer, offset, length, out);
            }
        }
        else
        {
            ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
            emitScalar(field, reader, out);
        }
    }

    private void emitScalar(
        ProtobufField field,
        ProtobufReader reader,
        JsonGeneratorEx out)
    {
        switch (field.type())
        {
        case INT32:
            out.write(reader.readVarint32());
            break;
        case UINT32:
            out.write(reader.readVarint32() & 0xffffffffL);
            break;
        case SINT32:
            out.write(reader.readZigzag32());
            break;
        case FIXED32:
            out.write(reader.readFixed32() & 0xffffffffL);
            break;
        case SFIXED32:
            out.write(reader.readFixed32());
            break;
        case INT64:
            out.write(Long.toString(reader.readVarint64()));
            break;
        case UINT64:
            out.write(Long.toUnsignedString(reader.readVarint64()));
            break;
        case SINT64:
            out.write(Long.toString(reader.readZigzag64()));
            break;
        case FIXED64:
            out.write(Long.toUnsignedString(reader.readFixed64()));
            break;
        case SFIXED64:
            out.write(Long.toString(reader.readFixed64()));
            break;
        case BOOL:
            out.write(reader.readVarint64() != 0L);
            break;
        case DOUBLE:
            emitDouble(Double.longBitsToDouble(reader.readFixed64()), out);
            break;
        case FLOAT:
            emitFloat(Float.intBitsToFloat(reader.readFixed32()), out);
            break;
        case STRING:
            out.write(readString(reader));
            break;
        case BYTES:
            out.write(base64.encodeToString(readBytes(reader)));
            break;
        case ENUM:
            emitEnum(field, reader.readVarint32(), out);
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private void emitEnum(
        ProtobufField field,
        int number,
        JsonGeneratorEx out)
    {
        ProtobufEnum enumeration = schema.resolveEnum(field);
        String name = enumeration.name(number);
        if (name != null)
        {
            out.write(name);
        }
        else
        {
            out.write(number);
        }
    }

    private void emitDouble(
        double value,
        JsonGeneratorEx out)
    {
        if (Double.isNaN(value))
        {
            out.write("NaN");
        }
        else if (value == Double.POSITIVE_INFINITY)
        {
            out.write("Infinity");
        }
        else if (value == Double.NEGATIVE_INFINITY)
        {
            out.write("-Infinity");
        }
        else
        {
            out.write(value);
        }
    }

    private void emitFloat(
        float value,
        JsonGeneratorEx out)
    {
        if (Float.isNaN(value))
        {
            out.write("NaN");
        }
        else if (value == Float.POSITIVE_INFINITY)
        {
            out.write("Infinity");
        }
        else if (value == Float.NEGATIVE_INFINITY)
        {
            out.write("-Infinity");
        }
        else
        {
            out.writeNumber(Float.toString(value));
        }
    }

    private void emitDefault(
        ProtobufField field,
        JsonGeneratorEx out)
    {
        switch (field.type())
        {
        case INT32:
        case SINT32:
        case SFIXED32:
            out.write(0);
            break;
        case UINT32:
        case FIXED32:
            out.write(0L);
            break;
        case INT64:
        case UINT64:
        case SINT64:
        case FIXED64:
        case SFIXED64:
            out.write("0");
            break;
        case BOOL:
            out.write(false);
            break;
        case DOUBLE:
        case FLOAT:
            out.write(0);
            break;
        case STRING:
            out.write("");
            break;
        case BYTES:
            out.write("");
            break;
        case ENUM:
            emitEnum(field, 0, out);
            break;
        default:
            out.writeStartObject();
            out.writeEnd();
            break;
        }
    }

    private String mapKey(
        ProtobufField keyField,
        ProtobufReader reader,
        ProtobufWireType wireType)
    {
        String key;
        switch (keyField.type())
        {
        case INT32:
        case SINT32:
            key = Integer.toString(keyField.type() == ProtobufType.SINT32
                ? reader.readZigzag32() : reader.readVarint32());
            break;
        case INT64:
        case SINT64:
            key = Long.toString(keyField.type() == ProtobufType.SINT64
                ? reader.readZigzag64() : reader.readVarint64());
            break;
        case UINT32:
            key = Long.toString(reader.readVarint32() & 0xffffffffL);
            break;
        case UINT64:
            key = Long.toUnsignedString(reader.readVarint64());
            break;
        case FIXED32:
            key = Long.toString(reader.readFixed32() & 0xffffffffL);
            break;
        case SFIXED32:
            key = Integer.toString(reader.readFixed32());
            break;
        case FIXED64:
            key = Long.toUnsignedString(reader.readFixed64());
            break;
        case SFIXED64:
            key = Long.toString(reader.readFixed64());
            break;
        case BOOL:
            key = Boolean.toString(reader.readVarint64() != 0L);
            break;
        case STRING:
            key = readString(reader);
            break;
        default:
            throw new ProtobufException("invalid map key type " + keyField.type());
        }
        return key;
    }

    private String mapKeyDefault(
        ProtobufField keyField)
    {
        return keyField.type() == ProtobufType.STRING ? "" :
            keyField.type() == ProtobufType.BOOL ? "false" : "0";
    }

    private boolean isMap(
        ProtobufField field)
    {
        ProtobufMessage message = field.composite() && field.typeName() != null
            ? schema.message(field.typeName()) : null;
        return message != null && message.mapEntry();
    }

    private void requireWireType(
        ProtobufField field,
        ProtobufWireType wireType,
        boolean repeated)
    {
        ProtobufWireType expected = field.type().wireType();
        boolean packedAllowed = repeated && field.type().packable() && wireType == ProtobufWireType.LEN;
        if (wireType != expected && !packedAllowed)
        {
            throw new ProtobufException("field " + field.number() + " wire type " + wireType +
                " incompatible with " + field.type());
        }
    }

    private String readString(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        String value = reader.buffer().getStringWithoutLengthUtf8(at, length);
        reader.skip(length);
        return value;
    }

    private byte[] readBytes(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        byte[] value = new byte[length];
        reader.buffer().getBytes(at, value);
        reader.skip(length);
        return value;
    }

    // well-known type handling -------------------------------------------------------------------

    private boolean emitWellKnown(
        String name,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        boolean handled = true;
        switch (name)
        {
        case ProtobufWellKnown.TIMESTAMP:
            ProtobufWellKnown.emitTimestamp(buffer, offset, length, out);
            break;
        case ProtobufWellKnown.DURATION:
            ProtobufWellKnown.emitDuration(buffer, offset, length, out);
            break;
        case ProtobufWellKnown.FIELD_MASK:
            ProtobufWellKnown.emitFieldMask(buffer, offset, length, out);
            break;
        case ProtobufWellKnown.EMPTY:
            out.writeStartObject();
            out.writeEnd();
            break;
        case ProtobufWellKnown.STRUCT:
            emitStruct(buffer, offset, length, out);
            break;
        case ProtobufWellKnown.LIST_VALUE:
            emitListValue(buffer, offset, length, out);
            break;
        default:
            handled = false;
            break;
        }
        return handled;
    }

    private boolean emitWellKnownValue(
        String typeName,
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        boolean handled = true;
        if (typeName == null)
        {
            handled = false;
        }
        else if (ProtobufWellKnown.isWrapper(typeName))
        {
            ProtobufWellKnown.emitWrapper(typeName, buffer, offset, length, base64, out);
        }
        else if (ProtobufWellKnown.VALUE.equals(typeName))
        {
            emitDynamicValue(buffer, offset, length, out);
        }
        else
        {
            switch (typeName)
            {
            case ProtobufWellKnown.TIMESTAMP:
                ProtobufWellKnown.emitTimestamp(buffer, offset, length, out);
                break;
            case ProtobufWellKnown.DURATION:
                ProtobufWellKnown.emitDuration(buffer, offset, length, out);
                break;
            case ProtobufWellKnown.FIELD_MASK:
                ProtobufWellKnown.emitFieldMask(buffer, offset, length, out);
                break;
            case ProtobufWellKnown.EMPTY:
                out.writeStartObject();
                out.writeEnd();
                break;
            case ProtobufWellKnown.STRUCT:
                emitStruct(buffer, offset, length, out);
                break;
            case ProtobufWellKnown.LIST_VALUE:
                emitListValue(buffer, offset, length, out);
                break;
            default:
                handled = false;
                break;
            }
        }
        return handled;
    }

    private void emitStruct(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        out.writeStartObject();
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1 && wireType == ProtobufWireType.LEN)
            {
                int entryLength = reader.readLength();
                int entryOffset = reader.offset();
                reader.skip(entryLength);
                emitStructEntry(buffer, entryOffset, entryLength, out);
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.writeEnd();
    }

    private void emitStructEntry(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        String key = "";
        int valueOffset = -1;
        int valueLength = 0;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1)
            {
                key = readString(reader);
            }
            else if (number == 2 && wireType == ProtobufWireType.LEN)
            {
                valueLength = reader.readLength();
                valueOffset = reader.offset();
                reader.skip(valueLength);
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.writeKey(key);
        if (valueOffset >= 0)
        {
            emitDynamicValue(buffer, valueOffset, valueLength, out);
        }
        else
        {
            out.writeNull();
        }
    }

    private void emitListValue(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        out.writeStartArray();
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1 && wireType == ProtobufWireType.LEN)
            {
                int valueLength = reader.readLength();
                int valueOffset = reader.offset();
                reader.skip(valueLength);
                emitDynamicValue(buffer, valueOffset, valueLength, out);
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.writeEnd();
    }

    private void emitDynamicValue(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        int kind = 0;
        boolean boolValue = false;
        double numberValue = 0;
        String stringValue = null;
        int nestedOffset = -1;
        int nestedLength = 0;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            switch (number)
            {
            case 1:
                reader.readVarint64();
                kind = 1;
                break;
            case 2:
                numberValue = Double.longBitsToDouble(reader.readFixed64());
                kind = 2;
                break;
            case 3:
                stringValue = readString(reader);
                kind = 3;
                break;
            case 4:
                boolValue = reader.readVarint64() != 0L;
                kind = 4;
                break;
            case 5:
                nestedLength = reader.readLength();
                nestedOffset = reader.offset();
                reader.skip(nestedLength);
                kind = 5;
                break;
            case 6:
                nestedLength = reader.readLength();
                nestedOffset = reader.offset();
                reader.skip(nestedLength);
                kind = 6;
                break;
            default:
                reader.skipField(wireType);
                break;
            }
        }

        switch (kind)
        {
        case 2:
            emitDouble(numberValue, out);
            break;
        case 3:
            out.write(stringValue);
            break;
        case 4:
            out.write(boolValue);
            break;
        case 5:
            emitStruct(buffer, nestedOffset, nestedLength, out);
            break;
        case 6:
            emitListValue(buffer, nestedOffset, nestedLength, out);
            break;
        default:
            out.writeNull();
            break;
        }
    }
}
