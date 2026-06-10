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

import java.time.Instant;
import java.util.Base64;
import java.util.StringJoiner;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * Proto3 JSON mappings for the {@code google.protobuf} well-known types that render as something
 * other than a plain object: timestamps and durations as strings, wrappers as their underlying
 * scalar, {@code FieldMask} as a comma-joined string, and {@code Empty} as {@code {}}. The
 * recursive {@code Struct}/{@code Value}/{@code ListValue} types are driven by {@link ProtobufDecoder}.
 */
final class ProtobufWellKnown
{
    static final String TIMESTAMP = "google.protobuf.Timestamp";
    static final String DURATION = "google.protobuf.Duration";
    static final String FIELD_MASK = "google.protobuf.FieldMask";
    static final String EMPTY = "google.protobuf.Empty";
    static final String STRUCT = "google.protobuf.Struct";
    static final String VALUE = "google.protobuf.Value";
    static final String LIST_VALUE = "google.protobuf.ListValue";

    static final String DOUBLE_VALUE = "google.protobuf.DoubleValue";
    static final String FLOAT_VALUE = "google.protobuf.FloatValue";
    static final String INT64_VALUE = "google.protobuf.Int64Value";
    static final String UINT64_VALUE = "google.protobuf.UInt64Value";
    static final String INT32_VALUE = "google.protobuf.Int32Value";
    static final String UINT32_VALUE = "google.protobuf.UInt32Value";
    static final String BOOL_VALUE = "google.protobuf.BoolValue";
    static final String STRING_VALUE = "google.protobuf.StringValue";
    static final String BYTES_VALUE = "google.protobuf.BytesValue";

    static boolean isWrapper(
        String typeName)
    {
        return DOUBLE_VALUE.equals(typeName) || FLOAT_VALUE.equals(typeName) ||
            INT64_VALUE.equals(typeName) || UINT64_VALUE.equals(typeName) ||
            INT32_VALUE.equals(typeName) || UINT32_VALUE.equals(typeName) ||
            BOOL_VALUE.equals(typeName) || STRING_VALUE.equals(typeName) ||
            BYTES_VALUE.equals(typeName);
    }

    static void emitWrapper(
        String typeName,
        DirectBuffer buffer,
        int offset,
        int length,
        Base64.Encoder base64,
        JsonGeneratorEx out)
    {
        ProtobufReader reader = scanTo(buffer, offset, length, 1);
        boolean present = reader != null;
        switch (typeName)
        {
        case DOUBLE_VALUE:
            out.write(present ? Double.longBitsToDouble(reader.readFixed64()) : 0);
            break;
        case FLOAT_VALUE:
            out.writeNumber(Float.toString(present ? Float.intBitsToFloat(reader.readFixed32()) : 0f));
            break;
        case INT64_VALUE:
            out.write(Long.toString(present ? reader.readVarint64() : 0L));
            break;
        case UINT64_VALUE:
            out.write(Long.toUnsignedString(present ? reader.readVarint64() : 0L));
            break;
        case INT32_VALUE:
            out.write(present ? reader.readVarint32() : 0);
            break;
        case UINT32_VALUE:
            out.write(present ? reader.readVarint32() & 0xffffffffL : 0L);
            break;
        case BOOL_VALUE:
            out.write(present && reader.readVarint64() != 0L);
            break;
        case STRING_VALUE:
            out.write(present ? readString(reader) : "");
            break;
        case BYTES_VALUE:
            out.write(present ? base64.encodeToString(readBytes(reader)) : "");
            break;
        default:
            out.writeNull();
            break;
        }
    }

    static void emitTimestamp(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        long seconds = 0;
        int nanos = 0;
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1)
            {
                seconds = reader.readVarint64();
            }
            else if (number == 2)
            {
                nanos = reader.readVarint32();
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.write(Instant.ofEpochSecond(seconds, nanos).toString());
    }

    static void emitDuration(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        long seconds = 0;
        int nanos = 0;
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1)
            {
                seconds = reader.readVarint64();
            }
            else if (number == 2)
            {
                nanos = reader.readVarint32();
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.write(formatDuration(seconds, nanos));
    }

    static String formatDuration(
        long seconds,
        int nanos)
    {
        StringBuilder text = new StringBuilder();
        if (seconds < 0 || nanos < 0)
        {
            text.append('-');
            seconds = Math.abs(seconds);
            nanos = Math.abs(nanos);
        }
        text.append(seconds);
        if (nanos != 0)
        {
            String fraction = String.format("%09d", nanos);
            int digits = nanos % 1_000_000 == 0 ? 3 : nanos % 1_000 == 0 ? 6 : 9;
            text.append('.').append(fraction, 0, digits);
        }
        text.append('s');
        return text.toString();
    }

    static void emitFieldMask(
        DirectBuffer buffer,
        int offset,
        int length,
        JsonGeneratorEx out)
    {
        StringJoiner joiner = new StringJoiner(",");
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == 1 && wireType == ProtobufWireType.LEN)
            {
                joiner.add(toCamelPath(readString(reader)));
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        out.write(joiner.toString());
    }

    private static String toCamelPath(
        String path)
    {
        StringJoiner joiner = new StringJoiner(".");
        int start = 0;
        for (int i = 0; i <= path.length(); i++)
        {
            if (i == path.length() || path.charAt(i) == '.')
            {
                joiner.add(ProtobufField.toJsonName(path.substring(start, i)));
                start = i + 1;
            }
        }
        return joiner.toString();
    }

    private static ProtobufReader scanTo(
        DirectBuffer buffer,
        int offset,
        int length,
        int target)
    {
        ProtobufReader reader = new ProtobufReader().wrap(buffer, offset, length);
        ProtobufReader found = null;
        while (reader.hasRemaining())
        {
            int tag = reader.readVarint32();
            int number = tag >>> 3;
            ProtobufWireType wireType = ProtobufWireType.of(tag & 0x7);
            if (number == target)
            {
                found = reader;
                break;
            }
            else
            {
                reader.skipField(wireType);
            }
        }
        return found;
    }

    private static String readString(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        String value = reader.buffer().getStringWithoutLengthUtf8(at, length);
        reader.skip(length);
        return value;
    }

    private static byte[] readBytes(
        ProtobufReader reader)
    {
        int length = reader.readLength();
        int at = reader.offset();
        byte[] value = new byte[length];
        reader.buffer().getBytes(at, value);
        reader.skip(length);
        return value;
    }

    private ProtobufWellKnown()
    {
    }
}
