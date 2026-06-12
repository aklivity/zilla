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
package io.aklivity.zilla.runtime.common.json.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

/**
 * Streaming, compact {@link JsonGeneratorEx} that writes directly into a {@link
 * MutableDirectBuffer} with no intermediate DOM and no per-call allocation. Structural separators
 * ({@code ,} and {@code :}) and quoting are inserted automatically from an internal context
 * stack; output is emitted in source order with no insignificant whitespace.
 * <p>
 * {@code common-json} ships this implementation; it requires no {@code jakarta.json} provider on
 * the classpath. The DOM-coupled {@code write} overloads that accept a {@link JsonValue} are
 * unsupported, mirroring the way the parser declines its DOM accessors.
 */
public final class JsonGeneratorImpl implements JsonGeneratorEx
{
    private static final int MAX_DEPTH = 64;
    private static final byte[] HEX = "0123456789abcdef".getBytes();

    private final boolean[] inArray = new boolean[MAX_DEPTH];
    private final boolean[] hasMembers = new boolean[MAX_DEPTH];

    private MutableDirectBuffer buffer;
    private int offset;
    private int progress;
    private int limit;
    private int depth;
    private boolean afterKey;

    @Override
    public JsonGeneratorImpl wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit)
    {
        assert offset <= limit && limit <= buffer.capacity();
        this.buffer = buffer;
        this.offset = offset;
        this.progress = offset;
        this.limit = limit;
        return this;
    }

    @Override
    public int length()
    {
        return progress - offset;
    }

    @Override
    public int remaining()
    {
        return limit - progress;
    }

    @Override
    public JsonGeneratorImpl writeStartObject()
    {
        preValue();
        putByte('{');
        push(false);
        return this;
    }

    @Override
    public JsonGeneratorImpl writeStartObject(
        String name)
    {
        writeKey(name);
        return writeStartObject();
    }

    @Override
    public JsonGeneratorImpl writeStartArray()
    {
        preValue();
        putByte('[');
        push(true);
        return this;
    }

    @Override
    public JsonGeneratorImpl writeStartArray(
        String name)
    {
        writeKey(name);
        return writeStartArray();
    }

    @Override
    public JsonGeneratorImpl writeKey(
        String name)
    {
        if (hasMembers[depth - 1])
        {
            putByte(',');
        }
        hasMembers[depth - 1] = true;
        writeString(name);
        putByte(':');
        afterKey = true;
        return this;
    }

    @Override
    public JsonGeneratorImpl writeEnd()
    {
        depth--;
        putByte(inArray[depth] ? ']' : '}');
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        String value)
    {
        preValue();
        writeString(value);
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        BigDecimal value)
    {
        return writeNumber(value.toString());
    }

    @Override
    public JsonGeneratorImpl write(
        BigInteger value)
    {
        return writeNumber(value.toString());
    }

    @Override
    public JsonGeneratorImpl write(
        int value)
    {
        return writeNumber(Integer.toString(value));
    }

    @Override
    public JsonGeneratorImpl write(
        long value)
    {
        return writeNumber(Long.toString(value));
    }

    @Override
    public JsonGeneratorImpl write(
        double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            throw new NumberFormatException("not a valid JSON number: " + value);
        }
        return writeNumber(Double.toString(value));
    }

    @Override
    public JsonGeneratorImpl write(
        boolean value)
    {
        preValue();
        writeAscii(value ? "true" : "false");
        return this;
    }

    @Override
    public JsonGeneratorImpl writeNull()
    {
        preValue();
        writeAscii("null");
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        String value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        BigInteger value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        BigDecimal value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        int value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        long value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        double value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        boolean value)
    {
        writeKey(name);
        return write(value);
    }

    @Override
    public JsonGeneratorImpl writeNull(
        String name)
    {
        writeKey(name);
        return writeNull();
    }

    @Override
    public JsonGeneratorImpl write(
        JsonValue value)
    {
        switch (value.getValueType())
        {
        case OBJECT -> writeObject(value.asJsonObject());
        case ARRAY -> writeArray(value.asJsonArray());
        case STRING -> write(((JsonString) value).getString());
        case NUMBER -> writeNumber(value.toString());
        case TRUE -> write(true);
        case FALSE -> write(false);
        case NULL -> writeNull();
        default ->
        {
        }
        }
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        String name,
        JsonValue value)
    {
        writeKey(name);
        return write(value);
    }

    private void writeObject(
        JsonObject object)
    {
        writeStartObject();
        for (Map.Entry<String, JsonValue> entry : object.entrySet())
        {
            write(entry.getKey(), entry.getValue());
        }
        writeEnd();
    }

    private void writeArray(
        JsonArray array)
    {
        writeStartArray();
        for (JsonValue element : array)
        {
            write(element);
        }
        writeEnd();
    }

    @Override
    public JsonGeneratorImpl writeNumber(
        String literal)
    {
        preValue();
        writeAscii(literal);
        return this;
    }

    @Override
    public JsonGeneratorImpl writeRaw(
        DirectBuffer source,
        int index,
        int length)
    {
        preValue();
        buffer.putBytes(progress, source, index, length);
        progress += length;
        return this;
    }

    @Override
    public JsonGeneratorImpl writeRawContinue(
        DirectBuffer source,
        int index,
        int length)
    {
        buffer.putBytes(progress, source, index, length);
        progress += length;
        return this;
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }

    private void push(
        boolean array)
    {
        inArray[depth] = array;
        hasMembers[depth] = false;
        depth++;
        afterKey = false;
    }

    private void preValue()
    {
        if (afterKey)
        {
            afterKey = false;
        }
        else if (depth > 0 && inArray[depth - 1])
        {
            if (hasMembers[depth - 1])
            {
                putByte(',');
            }
            hasMembers[depth - 1] = true;
        }
    }

    private void writeString(
        String value)
    {
        putByte('"');
        int index = 0;
        int length = value.length();
        while (index < length)
        {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            switch (codePoint)
            {
            case '"':
                putByte('\\');
                putByte('"');
                break;
            case '\\':
                putByte('\\');
                putByte('\\');
                break;
            case '\n':
                putByte('\\');
                putByte('n');
                break;
            case '\r':
                putByte('\\');
                putByte('r');
                break;
            case '\t':
                putByte('\\');
                putByte('t');
                break;
            case '\b':
                putByte('\\');
                putByte('b');
                break;
            case '\f':
                putByte('\\');
                putByte('f');
                break;
            default:
                if (codePoint < 0x20)
                {
                    writeUnicodeEscape(codePoint);
                }
                else
                {
                    writeUtf8(codePoint);
                }
                break;
            }
        }
        putByte('"');
    }

    private void writeUtf8(
        int codePoint)
    {
        if (codePoint < 0x80)
        {
            putByte(codePoint);
        }
        else if (codePoint < 0x800)
        {
            putByte(0xc0 | codePoint >> 6);
            putByte(0x80 | codePoint & 0x3f);
        }
        else if (codePoint < 0x10000)
        {
            putByte(0xe0 | codePoint >> 12);
            putByte(0x80 | codePoint >> 6 & 0x3f);
            putByte(0x80 | codePoint & 0x3f);
        }
        else
        {
            putByte(0xf0 | codePoint >> 18);
            putByte(0x80 | codePoint >> 12 & 0x3f);
            putByte(0x80 | codePoint >> 6 & 0x3f);
            putByte(0x80 | codePoint & 0x3f);
        }
    }

    private void writeUnicodeEscape(
        int codePoint)
    {
        putByte('\\');
        putByte('u');
        putByte(HEX[codePoint >> 12 & 0xf]);
        putByte(HEX[codePoint >> 8 & 0xf]);
        putByte(HEX[codePoint >> 4 & 0xf]);
        putByte(HEX[codePoint & 0xf]);
    }

    private void writeAscii(
        String value)
    {
        for (int index = 0; index < value.length(); index++)
        {
            putByte(value.charAt(index));
        }
    }

    private void putByte(
        int value)
    {
        buffer.putByte(progress, (byte) value);
        progress++;
    }
}
