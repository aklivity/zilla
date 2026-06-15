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
import java.util.function.IntConsumer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

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
    private final IntConsumer putByte;
    private final SegmentWriter writeSegment;

    private MutableDirectBuffer buffer;
    private int offset;
    private int progress;
    private int limit;
    private int depth;
    private int consumed;
    // at most one of these positions holds at a time: a value is expected after a key, or an
    // incomplete string/number fragment is open awaiting its remaining fragments
    private Pending pending = Pending.NONE;

    public JsonGeneratorImpl()
    {
        this(Map.of());
    }

    public JsonGeneratorImpl(
        Map<String, ?> config)
    {
        final boolean escaped = Boolean.TRUE.equals(config.get(JsonGeneratorEx.GENERATE_ESCAPED));
        this.putByte = escaped ? this::putEscaped : this::putRaw;
        this.writeSegment = escaped ? this::writeEscapedSegment : this::writeRawSegment;
    }

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
        this.consumed = 0;
        return this;
    }

    @Override
    public int length()
    {
        return progress - offset;
    }

    @Override
    public int consumed()
    {
        return consumed;
    }

    @Override
    public void reset()
    {
        this.depth = 0;
        this.pending = Pending.NONE;
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
        putByte.accept('{');
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
        putByte.accept('[');
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
        return writeKey((CharSequence) name);
    }

    @Override
    public JsonGeneratorImpl writeKey(
        CharSequence name)
    {
        if (hasMembers[depth - 1])
        {
            putByte.accept(',');
        }
        hasMembers[depth - 1] = true;
        writeString(name);
        putByte.accept(':');
        pending = Pending.AFTER_KEY;
        return this;
    }

    @Override
    public JsonGeneratorImpl writeEnd()
    {
        depth--;
        putByte.accept(inArray[depth] ? ']' : '}');
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        String value)
    {
        return write((CharSequence) value);
    }

    @Override
    public JsonGeneratorImpl write(
        CharSequence value)
    {
        preValue();
        writeString(value);
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        CharSequence value,
        Completion completion)
    {
        if (pending != Pending.STRING)
        {
            preValue();
            putByte.accept('"');
            pending = Pending.STRING;
        }
        writeStringBody(value);
        if (completion == Completion.COMPLETE)
        {
            putByte.accept('"');
            pending = Pending.NONE;
        }
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
        return writeNumber((CharSequence) literal);
    }

    @Override
    public JsonGeneratorImpl writeNumber(
        CharSequence literal)
    {
        preValue();
        writeAscii(literal);
        return this;
    }

    @Override
    public JsonGeneratorImpl writeNumber(
        CharSequence literal,
        Completion completion)
    {
        if (pending != Pending.NUMBER)
        {
            preValue();
            pending = Pending.NUMBER;
        }
        writeAscii(literal);
        if (completion == Completion.COMPLETE)
        {
            pending = Pending.NONE;
        }
        return this;
    }

    @Override
    public JsonGeneratorImpl writeRaw(
        DirectBuffer source,
        int index,
        int length)
    {
        preValue();
        int written = writeSegment.accept(source, index, length);
        assert written == length;
        return this;
    }

    @Override
    public JsonGeneratorImpl writeSegment(
        DirectBuffer source,
        int index,
        int length)
    {
        writeSegment.accept(source, index, length);
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
        pending = Pending.NONE;
    }

    private void preValue()
    {
        if (pending == Pending.AFTER_KEY)
        {
            pending = Pending.NONE;
        }
        else if (depth > 0 && inArray[depth - 1])
        {
            if (hasMembers[depth - 1])
            {
                putByte.accept(',');
            }
            hasMembers[depth - 1] = true;
        }
    }

    private void writeString(
        CharSequence value)
    {
        putByte.accept('"');
        writeStringBody(value);
        putByte.accept('"');
    }

    private void writeStringBody(
        CharSequence value)
    {
        int index = 0;
        int length = value.length();
        while (index < length)
        {
            int codePoint = Character.codePointAt(value, index);
            index += Character.charCount(codePoint);
            switch (codePoint)
            {
            case '"':
                putByte.accept('\\');
                putByte.accept('"');
                break;
            case '\\':
                putByte.accept('\\');
                putByte.accept('\\');
                break;
            case '\n':
                putByte.accept('\\');
                putByte.accept('n');
                break;
            case '\r':
                putByte.accept('\\');
                putByte.accept('r');
                break;
            case '\t':
                putByte.accept('\\');
                putByte.accept('t');
                break;
            case '\b':
                putByte.accept('\\');
                putByte.accept('b');
                break;
            case '\f':
                putByte.accept('\\');
                putByte.accept('f');
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
    }

    private void writeUtf8(
        int codePoint)
    {
        if (codePoint < 0x80)
        {
            putByte.accept(codePoint);
        }
        else if (codePoint < 0x800)
        {
            putByte.accept(0xc0 | codePoint >> 6);
            putByte.accept(0x80 | codePoint & 0x3f);
        }
        else if (codePoint < 0x10000)
        {
            putByte.accept(0xe0 | codePoint >> 12);
            putByte.accept(0x80 | codePoint >> 6 & 0x3f);
            putByte.accept(0x80 | codePoint & 0x3f);
        }
        else
        {
            putByte.accept(0xf0 | codePoint >> 18);
            putByte.accept(0x80 | codePoint >> 12 & 0x3f);
            putByte.accept(0x80 | codePoint >> 6 & 0x3f);
            putByte.accept(0x80 | codePoint & 0x3f);
        }
    }

    private void writeUnicodeEscape(
        int codePoint)
    {
        putByte.accept('\\');
        putByte.accept('u');
        putByte.accept(HEX[codePoint >> 12 & 0xf]);
        putByte.accept(HEX[codePoint >> 8 & 0xf]);
        putByte.accept(HEX[codePoint >> 4 & 0xf]);
        putByte.accept(HEX[codePoint & 0xf]);
    }

    private void writeAscii(
        CharSequence value)
    {
        for (int index = 0; index < value.length(); index++)
        {
            putByte.accept(value.charAt(index));
        }
    }

    private int writeRawSegment(
        DirectBuffer source,
        int index,
        int length)
    {
        int written = Math.min(length, limit - progress);
        buffer.putBytes(progress, source, index, written);
        progress += written;
        consumed += written;
        return written;
    }

    private int writeEscapedSegment(
        DirectBuffer source,
        int index,
        int length)
    {
        int written = 0;
        while (written < length)
        {
            int value = source.getByte(index + written) & 0xff;
            if (limit - progress < escapedWidth(value))
            {
                break;
            }
            putEscaped(value);
            written++;
        }
        consumed += written;
        return written;
    }

    private void putEscaped(
        int value)
    {
        value &= 0xff;
        switch (value)
        {
        case '"':
            putRaw('\\');
            putRaw('"');
            break;
        case '\\':
            putRaw('\\');
            putRaw('\\');
            break;
        case '\n':
            putRaw('\\');
            putRaw('n');
            break;
        case '\r':
            putRaw('\\');
            putRaw('r');
            break;
        case '\t':
            putRaw('\\');
            putRaw('t');
            break;
        case '\b':
            putRaw('\\');
            putRaw('b');
            break;
        case '\f':
            putRaw('\\');
            putRaw('f');
            break;
        default:
            if (value < 0x20)
            {
                putRaw('\\');
                putRaw('u');
                putRaw(HEX[value >> 12 & 0xf]);
                putRaw(HEX[value >> 8 & 0xf]);
                putRaw(HEX[value >> 4 & 0xf]);
                putRaw(HEX[value & 0xf]);
            }
            else
            {
                putRaw(value);
            }
            break;
        }
    }

    private static int escapedWidth(
        int value)
    {
        int width;
        switch (value)
        {
        case '"':
        case '\\':
        case '\n':
        case '\r':
        case '\t':
        case '\b':
        case '\f':
            width = 2;
            break;
        default:
            width = value < 0x20 ? 6 : 1;
            break;
        }
        return width;
    }

    private void putRaw(
        int value)
    {
        assert progress < limit;
        buffer.putByte(progress, (byte) value);
        progress++;
    }

    @FunctionalInterface
    private interface SegmentWriter
    {
        int accept(
            DirectBuffer source,
            int index,
            int length);
    }

    private enum Pending
    {
        NONE,
        AFTER_KEY,
        STRING,
        NUMBER
    }
}
