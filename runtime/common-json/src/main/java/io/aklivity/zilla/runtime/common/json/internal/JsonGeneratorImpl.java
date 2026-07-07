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
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntConsumer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonStep;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Streaming, compact {@link JsonGeneratorEx} that writes directly into a {@link
 * MutableDirectBufferEx} with no intermediate DOM and no per-call allocation. Structural separators
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
    // a defensible initial target so a freshly constructed generator never NPEs on direct use before wrap
    private static final MutableDirectBufferEx EMPTY = new UnsafeBufferEx(new byte[0]);

    private final boolean[] inArray = new boolean[MAX_DEPTH];
    private final boolean[] hasMembers = new boolean[MAX_DEPTH];
    private final IntConsumer putByte;
    private final SegmentWriter writeSegment;
    private final boolean escaped;

    private MutableDirectBufferEx buffer = EMPTY;
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
        this.escaped = Boolean.TRUE.equals(config.get(JsonGeneratorEx.GENERATE_ESCAPED));
        this.putByte = escaped ? this::putEscaped : this::putRaw;
        this.writeSegment = escaped ? this::writeEscapedSegment : this::writeRawSegment;
    }

    @Override
    public boolean identity()
    {
        return !escaped;
    }

    @Override
    public JsonGeneratorImpl wrap(
        MutableDirectBufferEx buffer,
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
        return writeKey(name, Completion.COMPLETE);
    }

    @Override
    public JsonGeneratorImpl writeKey(
        CharSequence name,
        Completion completion)
    {
        if (pending != Pending.KEY)
        {
            // see write(CharSequence, Completion)'s equivalent guard: the opening comma/quote sit outside
            // writeStringBody's own bound check, and an empty, already-complete key never enters that loop
            // to have its own closing reserve (here: quote + key separator) enforced
            final boolean closesEmpty = completion == Completion.COMPLETE && name.length() == 0;
            if (remaining() < (hasMembers[depth - 1] ? 1 : 0) + 1 + (closesEmpty ? 2 : 0))
            {
                return this;
            }
            if (hasMembers[depth - 1])
            {
                putByte.accept(',');
            }
            hasMembers[depth - 1] = true;
            putByte.accept('"');
            pending = Pending.KEY;
        }
        // emit only the code points whose escaped form fits the output bound, reserving room on the final
        // fragment for the closing quote and key separator; report the source chars taken via consumed() so a
        // chunking driver advances its cursor and resumes from the remainder, the key-domain analog of
        // write(CharSequence, Completion)
        final int reserve = completion == Completion.COMPLETE ? 2 : 0;
        final int written = writeStringBody(name, reserve);
        consumed += written;
        // guards the resumed-continuation analog of the empty-key case above; see write(CharSequence,
        // Completion)'s equivalent comment
        if (written == name.length() && completion == Completion.COMPLETE && remaining() >= 2)
        {
            putByte.accept('"');
            putByte.accept(':');
            pending = Pending.AFTER_KEY;
        }
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
        return write(value, Completion.COMPLETE);
    }

    @Override
    public JsonGeneratorImpl write(
        CharSequence value,
        Completion completion)
    {
        if (pending != Pending.STRING)
        {
            // the opening quote (plus a possible preceding comma) is unconditional and outside
            // writeStringBody's own per-codepoint bound check, so it needs its own room check: without
            // it, wrapping the generator with little or no room left (as a caller draining a nearly-full
            // destination legitimately can) writes past limit uncaught. An empty, already-complete value
            // needs its closing quote reserved here too: writeStringBody's own loop never runs for a zero
            // length value, so it can't independently enforce that reserve the way it does for non-empty
            // ones, and deferring the close afterwards would be ambiguous — consumed() would read the same
            // (zero) whether this value is genuinely done or the close was merely deferred for lack of room
            final boolean closesEmpty = completion == Completion.COMPLETE && value.length() == 0;
            if (remaining() < (needsComma() ? 1 : 0) + 1 + (closesEmpty ? 1 : 0))
            {
                return this;
            }
            preValue();
            putByte.accept('"');
            pending = Pending.STRING;
        }
        // emit only the code points whose escaped form fits the output bound (reserving room for the
        // closing quote on the final fragment); report the source chars taken via consumed() so a chunking
        // driver advances its cursor and resumes from the remainder, the char-domain analog of writeSegment
        final int reserve = completion == Completion.COMPLETE ? 1 : 0;
        final int written = writeStringBody(value, reserve);
        consumed += written;
        // guards the resumed-continuation analog of the empty-value case above: a prior fragment already
        // opened the string, this call contributes no new characters, and completion turns COMPLETE — same
        // trivially-true written == length() with no loop iteration to have checked room for the close
        if (written == value.length() && completion == Completion.COMPLETE && remaining() >= 1)
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
        preValue();
        progress += buffer.putIntAscii(progress, value);
        return this;
    }

    @Override
    public JsonGeneratorImpl write(
        long value)
    {
        preValue();
        progress += buffer.putLongAscii(progress, value);
        return this;
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
        return writeNumber(literal, Completion.COMPLETE);
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
        // emit only the lexeme chars that fit the output bound and report them via consumed(), so a number
        // longer than the bound suspends and resumes from the remainder — the analog of write(value, …) for
        // a literal that carries no quoting or escaping
        final int written = writeAsciiBounded(literal);
        consumed += written;
        if (written == literal.length() && completion == Completion.COMPLETE)
        {
            pending = Pending.NONE;
        }
        return this;
    }

    @Override
    public JsonGeneratorImpl writeRaw(
        DirectBufferEx source,
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
        DirectBufferEx source,
        int index,
        int length)
    {
        writeSegment.accept(source, index, length);
        return this;
    }

    @Override
    public JsonGeneratorImpl writeSegment(
        DirectBufferEx source,
        int index,
        int length,
        Completion completion)
    {
        if (pending != Pending.SEGMENT)
        {
            // emit the value's leading separator once, before its first fragment
            preValue();
            pending = Pending.SEGMENT;
        }
        int written = writeSegment.accept(source, index, length);
        if (written == length && completion == Completion.COMPLETE)
        {
            pending = Pending.NONE;
        }
        return this;
    }

    @Override
    public JsonGeneratorImpl writeVerbatim(
        JsonVerbatim verbatim)
    {
        final Iterator<JsonStep> steps = verbatim.getSteps();
        final DirectBufferEx segment = verbatim.getSegment();
        // synthesize the leading separator only when the block begins a member/element that was first in the
        // source (its leading step is a member/element start rather than a SEPARATOR, so its bytes carry no
        // leading comma) yet the container already holds a member in the output — an injected value took the
        // first slot, displacing this former-first member. A leading SEPARATOR means the comma is in the bytes;
        // interior separators of a coalesced block likewise ride in the bytes. Source occupancy, not the bytes.
        final JsonStep first = steps.hasNext() ? steps.next() : null;
        if (first != null && needsSeparator(first))
        {
            putByte.accept(',');
        }
        copy(segment, 0, segment.capacity());
        // apply every step's structural effect (open/close depth, member occupancy) so the generator's state
        // stays coherent for an injected value that follows — state only, the bytes carried the structure
        if (first != null)
        {
            advance(first);
            while (steps.hasNext())
            {
                advance(steps.next());
            }
        }
        return this;
    }

    // Copies the verbatim block 1:1; the caller pre-bounds the pull to remaining(), so the bytes always fit.
    private void copy(
        DirectBufferEx source,
        int index,
        int length)
    {
        assert progress + length <= limit;
        buffer.putBytes(progress, source, index, length);
        progress += length;
    }

    // Whether the block begins a member (object key) or array element that was first in the source (so carries
    // no leading separator) into a container that already holds a member in the output — so one is synthesized.
    private boolean needsSeparator(
        JsonStep step)
    {
        boolean result = false;
        if (step != JsonStep.SEPARATOR && depth > 0 && hasMembers[depth - 1])
        {
            result = inArray[depth - 1] ? isValueStart(step) : step == JsonStep.KEY_NAME;
        }
        return result;
    }

    // Tracks the structural effect of a verbatim block without emitting (the bytes carried the structure): the
    // state-only analog of preValue()/writeKey()/push()/writeEnd(). A SEPARATOR rides in the bytes and moves no
    // state.
    private void advance(
        JsonStep step)
    {
        switch (step)
        {
        case START_OBJECT:
            markValueStart();
            push(false);
            break;
        case START_ARRAY:
            markValueStart();
            push(true);
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            hasMembers[depth - 1] = true;
            pending = Pending.AFTER_KEY;
            break;
        case VALUE:
            markValueStart();
            break;
        case START_DOCUMENT:
        case END_DOCUMENT:
            break;
        default:
            break;
        }
    }

    // The state-only effect of preValue() for a verbatim value: clear an after-key expectation, or mark the
    // current array occupied; no separator is emitted (the bytes carry it, or needsSeparator synthesized it).
    private void markValueStart()
    {
        if (pending == Pending.AFTER_KEY)
        {
            pending = Pending.NONE;
        }
        else if (depth > 0 && inArray[depth - 1])
        {
            hasMembers[depth - 1] = true;
        }
    }

    private static boolean isValueStart(
        JsonStep step)
    {
        return step == JsonStep.START_OBJECT || step == JsonStep.START_ARRAY || step == JsonStep.VALUE;
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

    // Peeks whether preValue() would emit a comma, without its hasMembers side effect, so a caller can
    // check room for both bytes atomically before committing to either (preValue() is not safe to retry:
    // it marks hasMembers[depth - 1] true unconditionally, so calling it and then bailing out for lack of
    // room would silently drop the comma on the next attempt).
    private boolean needsComma()
    {
        return pending != Pending.AFTER_KEY && depth > 0 && inArray[depth - 1] && hasMembers[depth - 1];
    }

    // Bounded counterpart used by write(CharSequence, Completion): emits whole code points while each one's
    // escaped width fits the output bound (minus reserve, held back for a trailing close-quote), stopping
    // on a code-point boundary so no UTF-8 char or escape is split; returns the count of source chars
    // emitted so the caller can advance its cursor and resume from the remainder.
    private int writeStringBody(
        CharSequence value,
        int reserve)
    {
        final int budget = limit - reserve;
        int index = 0;
        final int length = value.length();
        while (index < length)
        {
            final int codePoint = Character.codePointAt(value, index);
            if (budget - progress < codePointWidth(codePoint))
            {
                break;
            }
            index += Character.charCount(codePoint);
            emitStringCodePoint(codePoint);
        }
        return index;
    }

    private void emitStringCodePoint(
        int codePoint)
    {
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

    // Output byte width of a code point once written as canonical JSON string content: a short escape
    // (2 bytes), a control-char \\uXXXX escape (6), or its UTF-8 encoding (1-4). Mirrors emitStringCodePoint
    // for the verbatim (non GENERATE_ESCAPED) generator, which is the only mode the decoded value path uses.
    private static int codePointWidth(
        int codePoint)
    {
        int width;
        switch (codePoint)
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
            if (codePoint < 0x20)
            {
                width = 6;
            }
            else if (codePoint < 0x80)
            {
                width = 1;
            }
            else if (codePoint < 0x800)
            {
                width = 2;
            }
            else if (codePoint < 0x10000)
            {
                width = 3;
            }
            else
            {
                width = 4;
            }
            break;
        }
        return width;
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

    // Bounded counterpart for a fragmented/over-bound number lexeme: emits ASCII lexeme chars (each one
    // output byte, no escaping) while the bound has room, returning the count emitted so the caller can
    // advance its cursor and resume from the remainder.
    private int writeAsciiBounded(
        CharSequence value)
    {
        int index = 0;
        final int length = value.length();
        while (index < length && progress < limit)
        {
            putByte.accept(value.charAt(index));
            index++;
        }
        return index;
    }

    // Copies whole source units (UTF-8 char or JSON escape sequence), stopping before one that overflows
    // the output bound, so consumed() always advances on a unit boundary.
    private int writeRawSegment(
        DirectBufferEx source,
        int index,
        int length)
    {
        int written = 0;
        while (written < length)
        {
            int unit = unitLength(source, index + written, length - written);
            if (limit - progress < unit)
            {
                break;
            }
            buffer.putBytes(progress, source, index + written, unit);
            progress += unit;
            written += unit;
        }
        consumed += written;
        return written;
    }

    // Escapes whole source units (see writeRawSegment), stopping before a unit whose escaped width
    // would overflow the output bound.
    private int writeEscapedSegment(
        DirectBufferEx source,
        int index,
        int length)
    {
        int written = 0;
        while (written < length)
        {
            int unit = unitLength(source, index + written, length - written);
            if (limit - progress < escapedUnitWidth(source, index + written, unit))
            {
                break;
            }
            for (int i = 0; i < unit; i++)
            {
                putEscaped(source.getByte(index + written + i) & 0xff);
            }
            written += unit;
        }
        consumed += written;
        return written;
    }

    // Byte length of the next source unit: a backslash escape (2 or 6 bytes) or a UTF-8 char (1-4 bytes),
    // capped at length so a unit not wholly available is held back rather than split.
    private static int unitLength(
        DirectBufferEx source,
        int index,
        int length)
    {
        int first = source.getByte(index) & 0xff;
        int unit;
        if (first == '\\')
        {
            int next = length > 1 ? source.getByte(index + 1) & 0xff : -1;
            unit = next == 'u' ? 6 : 2;
        }
        else if (first < 0x80)
        {
            unit = 1;
        }
        else if ((first & 0xe0) == 0xc0)
        {
            unit = 2;
        }
        else if ((first & 0xf0) == 0xe0)
        {
            unit = 3;
        }
        else if ((first & 0xf8) == 0xf0)
        {
            unit = 4;
        }
        else
        {
            unit = 1;
        }
        return Math.min(unit, length);
    }

    // Output byte width of a source unit once escaped: the sum of each byte's escaped width.
    private static int escapedUnitWidth(
        DirectBufferEx source,
        int index,
        int unit)
    {
        int width = 0;
        for (int i = 0; i < unit; i++)
        {
            width += escapedWidth(source.getByte(index + i) & 0xff);
        }
        return width;
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
            DirectBufferEx source,
            int index,
            int length);
    }

    private enum Pending
    {
        NONE,
        AFTER_KEY,
        KEY,
        STRING,
        NUMBER,
        SEGMENT
    }
}
