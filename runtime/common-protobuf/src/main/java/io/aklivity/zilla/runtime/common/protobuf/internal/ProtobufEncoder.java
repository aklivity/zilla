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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Encodes the proto3 JSON mapping of a message, read from a {@link JsonParser}, into Protobuf wire
 * format against a {@link ProtobufSchema}.
 * <p>
 * A length-delimited nested message must be prefixed by its encoded length, which is unknown until
 * its body is built; since JSON is forward-only the body is staged in a per-depth scratch buffer,
 * then spliced with its length into the parent. Staging is bounded by message nesting depth.
 */
public final class ProtobufEncoder
{
    private final ProtobufSchema schema;
    private final Base64.Decoder base64;
    private final List<ExpandableArrayBuffer> scratch;
    private final List<ProtobufWriter> writers;

    public ProtobufEncoder(
        ProtobufSchema schema)
    {
        this.schema = schema;
        this.base64 = Base64.getDecoder();
        this.scratch = new ArrayList<>();
        this.writers = new ArrayList<>();
    }

    public int encode(
        String messageName,
        JsonParser parser,
        MutableDirectBuffer out,
        int offset)
    {
        ProtobufMessage message = schema.message(messageName);
        if (message == null)
        {
            throw new ProtobufException("unknown message " + messageName);
        }
        ProtobufWriter writer = new ProtobufWriter().wrap(out, offset);
        expect(parser.next(), Event.START_OBJECT);
        encodeBody(message, parser, writer, 1);
        return writer.length();
    }

    private void encodeBody(
        ProtobufMessage message,
        JsonParser parser,
        ProtobufWriter writer,
        int depth)
    {
        Event event;
        while ((event = parser.next()) != Event.END_OBJECT)
        {
            String name = parser.getString();
            ProtobufField field = message.field(name);
            Event value = parser.next();
            if (field == null)
            {
                skipValue(parser, value);
            }
            else if (field.repeated() && isMap(field))
            {
                encodeMap(field, parser, value, writer, depth);
            }
            else if (field.repeated())
            {
                encodeRepeated(field, parser, value, writer, depth);
            }
            else
            {
                encodeField(field, parser, value, writer, depth);
            }
        }
    }

    private void encodeRepeated(
        ProtobufField field,
        JsonParser parser,
        Event value,
        ProtobufWriter writer,
        int depth)
    {
        expect(value, Event.START_ARRAY);
        if (field.type().packable())
        {
            ProtobufWriter block = acquire(depth).wrap(scratch(depth), 0);
            Event element;
            while ((element = parser.next()) != Event.END_ARRAY)
            {
                encodeScalarValue(field, parser, element, block);
            }
            if (block.length() > 0)
            {
                writer.writeTag(field.number(), ProtobufWireType.LEN);
                writer.writeBytes(scratch(depth), 0, block.length());
            }
        }
        else
        {
            Event element;
            while ((element = parser.next()) != Event.END_ARRAY)
            {
                encodeField(field, parser, element, writer, depth);
            }
        }
    }

    private void encodeMap(
        ProtobufField field,
        JsonParser parser,
        Event value,
        ProtobufWriter writer,
        int depth)
    {
        expect(value, Event.START_OBJECT);
        ProtobufMessage entry = schema.resolveMessage(field);
        ProtobufField keyField = entry.mapKey();
        ProtobufField valueField = entry.mapValue();
        Event event;
        while ((event = parser.next()) != Event.END_OBJECT)
        {
            String key = parser.getString();
            Event valueEvent = parser.next();

            ProtobufWriter entryWriter = acquire(depth).wrap(scratch(depth), 0);
            encodeMapKey(keyField, key, entryWriter);
            encodeField(valueField, parser, valueEvent, entryWriter, depth + 1);

            writer.writeTag(field.number(), ProtobufWireType.LEN);
            writer.writeBytes(scratch(depth), 0, entryWriter.length());
        }
    }

    private void encodeField(
        ProtobufField field,
        JsonParser parser,
        Event value,
        ProtobufWriter writer,
        int depth)
    {
        if (field.composite())
        {
            ProtobufWriter body = acquire(depth).wrap(scratch(depth), 0);
            if (!encodeWellKnown(field.typeName(), parser, value, body, depth + 1))
            {
                expect(value, Event.START_OBJECT);
                ProtobufMessage message = schema.resolveMessage(field);
                encodeBody(message, parser, body, depth + 1);
            }
            writer.writeTag(field.number(), ProtobufWireType.LEN);
            writer.writeBytes(scratch(depth), 0, body.length());
        }
        else
        {
            encodeScalarTagged(field, parser, value, writer);
        }
    }

    private void encodeScalarTagged(
        ProtobufField field,
        JsonParser parser,
        Event value,
        ProtobufWriter writer)
    {
        writer.writeTag(field.number(), field.type().wireType());
        encodeScalarValue(field, parser, value, writer);
    }

    private void encodeScalarValue(
        ProtobufField field,
        JsonParser parser,
        Event value,
        ProtobufWriter writer)
    {
        switch (field.type())
        {
        case INT32:
            writer.writeVarint64(intValue(parser, value));
            break;
        case UINT32:
            writer.writeVarint64(longValue(parser, value) & 0xffffffffL);
            break;
        case SINT32:
            writer.writeZigzag32(intValue(parser, value));
            break;
        case FIXED32:
            writer.writeFixed32((int) longValue(parser, value));
            break;
        case SFIXED32:
            writer.writeFixed32(intValue(parser, value));
            break;
        case INT64:
            writer.writeVarint64(longValue(parser, value));
            break;
        case UINT64:
            writer.writeVarint64(longUnsignedValue(parser, value));
            break;
        case SINT64:
            writer.writeZigzag64(longValue(parser, value));
            break;
        case FIXED64:
            writer.writeFixed64(longUnsignedValue(parser, value));
            break;
        case SFIXED64:
            writer.writeFixed64(longValue(parser, value));
            break;
        case BOOL:
            writer.writeVarint64(boolValue(parser, value) ? 1L : 0L);
            break;
        case DOUBLE:
            writer.writeFixed64(Double.doubleToLongBits(doubleValue(parser, value)));
            break;
        case FLOAT:
            writer.writeFixed32(Float.floatToIntBits((float) doubleValue(parser, value)));
            break;
        case STRING:
            writer.writeBytes(parser.getString().getBytes(StandardCharsets.UTF_8));
            break;
        case BYTES:
            writer.writeBytes(base64.decode(parser.getString()));
            break;
        case ENUM:
            writer.writeVarint64(enumValue(field, parser, value));
            break;
        default:
            throw new ProtobufException("unsupported scalar type " + field.type());
        }
    }

    private void encodeMapKey(
        ProtobufField keyField,
        String key,
        ProtobufWriter writer)
    {
        writer.writeTag(1, keyField.type().wireType());
        switch (keyField.type())
        {
        case INT32:
            writer.writeVarint64(Integer.parseInt(key));
            break;
        case SINT32:
            writer.writeZigzag32(Integer.parseInt(key));
            break;
        case UINT32:
            writer.writeVarint64(Long.parseLong(key) & 0xffffffffL);
            break;
        case FIXED32:
            writer.writeFixed32((int) Long.parseLong(key));
            break;
        case SFIXED32:
            writer.writeFixed32(Integer.parseInt(key));
            break;
        case INT64:
            writer.writeVarint64(Long.parseLong(key));
            break;
        case UINT64:
            writer.writeVarint64(Long.parseUnsignedLong(key));
            break;
        case SINT64:
            writer.writeZigzag64(Long.parseLong(key));
            break;
        case FIXED64:
            writer.writeFixed64(Long.parseUnsignedLong(key));
            break;
        case SFIXED64:
            writer.writeFixed64(Long.parseLong(key));
            break;
        case BOOL:
            writer.writeVarint64(Boolean.parseBoolean(key) ? 1L : 0L);
            break;
        case STRING:
            writer.writeBytes(key.getBytes(StandardCharsets.UTF_8));
            break;
        default:
            throw new ProtobufException("invalid map key type " + keyField.type());
        }
    }

    private long enumValue(
        ProtobufField field,
        JsonParser parser,
        Event value)
    {
        long number;
        if (value == Event.VALUE_STRING)
        {
            ProtobufEnum enumeration = schema.resolveEnum(field);
            Integer resolved = enumeration.number(parser.getString());
            if (resolved == null)
            {
                throw new ProtobufException("unknown enum value " + parser.getString());
            }
            number = resolved;
        }
        else
        {
            number = intValue(parser, value);
        }
        return number;
    }

    private boolean isMap(
        ProtobufField field)
    {
        ProtobufMessage message = field.composite() && field.typeName() != null
            ? schema.message(field.typeName()) : null;
        return message != null && message.mapEntry();
    }

    // well-known type handling -------------------------------------------------------------------

    private boolean encodeWellKnown(
        String typeName,
        JsonParser parser,
        Event value,
        ProtobufWriter body,
        int depth)
    {
        boolean handled = true;
        if (typeName == null)
        {
            handled = false;
        }
        else if (ProtobufWellKnown.isWrapper(typeName))
        {
            encodeWrapper(typeName, parser, value, body);
        }
        else
        {
            switch (typeName)
            {
            case ProtobufWellKnown.TIMESTAMP:
                encodeTimestamp(parser, body);
                break;
            case ProtobufWellKnown.DURATION:
                encodeDuration(parser, body);
                break;
            case ProtobufWellKnown.FIELD_MASK:
                encodeFieldMask(parser, body);
                break;
            case ProtobufWellKnown.EMPTY:
                expect(value, Event.START_OBJECT);
                expect(parser.next(), Event.END_OBJECT);
                break;
            case ProtobufWellKnown.STRUCT:
                encodeStruct(parser, value, body, depth);
                break;
            case ProtobufWellKnown.VALUE:
                encodeDynamicValue(parser, value, body, depth);
                break;
            case ProtobufWellKnown.LIST_VALUE:
                encodeListValue(parser, value, body, depth);
                break;
            default:
                handled = false;
                break;
            }
        }
        return handled;
    }

    private void encodeStruct(
        JsonParser parser,
        Event value,
        ProtobufWriter body,
        int depth)
    {
        expect(value, Event.START_OBJECT);
        Event event;
        while ((event = parser.next()) != Event.END_OBJECT)
        {
            String key = parser.getString();
            Event valueEvent = parser.next();

            ProtobufWriter entry = acquire(depth).wrap(scratch(depth), 0);
            entry.writeTag(1, ProtobufWireType.LEN);
            entry.writeBytes(key.getBytes(StandardCharsets.UTF_8));
            ProtobufWriter valueBody = acquire(depth + 1).wrap(scratch(depth + 1), 0);
            encodeDynamicValue(parser, valueEvent, valueBody, depth + 2);
            entry.writeTag(2, ProtobufWireType.LEN);
            entry.writeBytes(scratch(depth + 1), 0, valueBody.length());

            body.writeTag(1, ProtobufWireType.LEN);
            body.writeBytes(scratch(depth), 0, entry.length());
        }
    }

    private void encodeListValue(
        JsonParser parser,
        Event value,
        ProtobufWriter body,
        int depth)
    {
        expect(value, Event.START_ARRAY);
        Event element;
        while ((element = parser.next()) != Event.END_ARRAY)
        {
            ProtobufWriter valueBody = acquire(depth).wrap(scratch(depth), 0);
            encodeDynamicValue(parser, element, valueBody, depth + 1);
            body.writeTag(1, ProtobufWireType.LEN);
            body.writeBytes(scratch(depth), 0, valueBody.length());
        }
    }

    private void encodeDynamicValue(
        JsonParser parser,
        Event value,
        ProtobufWriter body,
        int depth)
    {
        switch (value)
        {
        case VALUE_NULL:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(0L);
            break;
        case VALUE_NUMBER:
            body.writeTag(2, ProtobufWireType.I64);
            body.writeFixed64(Double.doubleToLongBits(parser.getBigDecimal().doubleValue()));
            break;
        case VALUE_STRING:
            body.writeTag(3, ProtobufWireType.LEN);
            body.writeBytes(parser.getString().getBytes(StandardCharsets.UTF_8));
            break;
        case VALUE_TRUE:
        case VALUE_FALSE:
            body.writeTag(4, ProtobufWireType.VARINT);
            body.writeVarint64(value == Event.VALUE_TRUE ? 1L : 0L);
            break;
        case START_OBJECT:
            ProtobufWriter structBody = acquire(depth).wrap(scratch(depth), 0);
            encodeStruct(parser, value, structBody, depth + 1);
            body.writeTag(5, ProtobufWireType.LEN);
            body.writeBytes(scratch(depth), 0, structBody.length());
            break;
        case START_ARRAY:
            ProtobufWriter listBody = acquire(depth).wrap(scratch(depth), 0);
            encodeListValue(parser, value, listBody, depth + 1);
            body.writeTag(6, ProtobufWireType.LEN);
            body.writeBytes(scratch(depth), 0, listBody.length());
            break;
        default:
            throw new ProtobufException("invalid value event " + value);
        }
    }

    private void encodeWrapper(
        String typeName,
        JsonParser parser,
        Event value,
        ProtobufWriter body)
    {
        switch (typeName)
        {
        case ProtobufWellKnown.DOUBLE_VALUE:
            body.writeTag(1, ProtobufWireType.I64);
            body.writeFixed64(Double.doubleToLongBits(doubleValue(parser, value)));
            break;
        case ProtobufWellKnown.FLOAT_VALUE:
            body.writeTag(1, ProtobufWireType.I32);
            body.writeFixed32(Float.floatToIntBits((float) doubleValue(parser, value)));
            break;
        case ProtobufWellKnown.INT64_VALUE:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(longValue(parser, value));
            break;
        case ProtobufWellKnown.UINT64_VALUE:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(longUnsignedValue(parser, value));
            break;
        case ProtobufWellKnown.INT32_VALUE:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(intValue(parser, value));
            break;
        case ProtobufWellKnown.UINT32_VALUE:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(longValue(parser, value) & 0xffffffffL);
            break;
        case ProtobufWellKnown.BOOL_VALUE:
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(boolValue(parser, value) ? 1L : 0L);
            break;
        case ProtobufWellKnown.STRING_VALUE:
            body.writeTag(1, ProtobufWireType.LEN);
            body.writeBytes(parser.getString().getBytes(StandardCharsets.UTF_8));
            break;
        case ProtobufWellKnown.BYTES_VALUE:
            body.writeTag(1, ProtobufWireType.LEN);
            body.writeBytes(base64.decode(parser.getString()));
            break;
        default:
            throw new ProtobufException("unsupported wrapper " + typeName);
        }
    }

    private void encodeTimestamp(
        JsonParser parser,
        ProtobufWriter body)
    {
        Instant instant = Instant.parse(parser.getString());
        long seconds = instant.getEpochSecond();
        int nanos = instant.getNano();
        if (seconds != 0)
        {
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(seconds);
        }
        if (nanos != 0)
        {
            body.writeTag(2, ProtobufWireType.VARINT);
            body.writeVarint64(nanos);
        }
    }

    private void encodeDuration(
        JsonParser parser,
        ProtobufWriter body)
    {
        String text = parser.getString();
        boolean negative = text.startsWith("-");
        if (negative)
        {
            text = text.substring(1);
        }
        if (text.endsWith("s"))
        {
            text = text.substring(0, text.length() - 1);
        }
        int dot = text.indexOf('.');
        long seconds = Long.parseLong(dot < 0 ? text : text.substring(0, dot));
        int nanos = 0;
        if (dot >= 0)
        {
            String fraction = (text.substring(dot + 1) + "000000000").substring(0, 9);
            nanos = Integer.parseInt(fraction);
        }
        if (negative)
        {
            seconds = -seconds;
            nanos = -nanos;
        }
        if (seconds != 0)
        {
            body.writeTag(1, ProtobufWireType.VARINT);
            body.writeVarint64(seconds);
        }
        if (nanos != 0)
        {
            body.writeTag(2, ProtobufWireType.VARINT);
            body.writeVarint64(nanos);
        }
    }

    private void encodeFieldMask(
        JsonParser parser,
        ProtobufWriter body)
    {
        String paths = parser.getString();
        if (!paths.isEmpty())
        {
            for (String path : paths.split(","))
            {
                body.writeTag(1, ProtobufWireType.LEN);
                body.writeBytes(toSnakePath(path).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static String toSnakePath(
        String path)
    {
        StringBuilder snake = new StringBuilder(path.length() + 4);
        for (int i = 0; i < path.length(); i++)
        {
            char ch = path.charAt(i);
            if (Character.isUpperCase(ch))
            {
                snake.append('_').append(Character.toLowerCase(ch));
            }
            else
            {
                snake.append(ch);
            }
        }
        return snake.toString();
    }

    // value coercion helpers ---------------------------------------------------------------------

    private int intValue(
        JsonParser parser,
        Event value)
    {
        return value == Event.VALUE_STRING ? Integer.parseInt(parser.getString()) : parser.getInt();
    }

    private long longValue(
        JsonParser parser,
        Event value)
    {
        return value == Event.VALUE_STRING ? Long.parseLong(parser.getString()) : parser.getLong();
    }

    private long longUnsignedValue(
        JsonParser parser,
        Event value)
    {
        return value == Event.VALUE_STRING ? Long.parseUnsignedLong(parser.getString()) : parser.getLong();
    }

    private boolean boolValue(
        JsonParser parser,
        Event value)
    {
        return value == Event.VALUE_TRUE ||
            value == Event.VALUE_STRING && Boolean.parseBoolean(parser.getString());
    }

    private double doubleValue(
        JsonParser parser,
        Event value)
    {
        double result;
        if (value == Event.VALUE_STRING)
        {
            String text = parser.getString();
            result = switch (text)
            {
            case "NaN" -> Double.NaN;
            case "Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(text);
            };
        }
        else
        {
            result = parser.getBigDecimal().doubleValue();
        }
        return result;
    }

    private void skipValue(
        JsonParser parser,
        Event value)
    {
        if (value == Event.START_OBJECT)
        {
            parser.skipObject();
        }
        else if (value == Event.START_ARRAY)
        {
            parser.skipArray();
        }
    }

    private void expect(
        Event actual,
        Event expected)
    {
        if (actual != expected)
        {
            throw new ProtobufException("expected " + expected + " but found " + actual);
        }
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
