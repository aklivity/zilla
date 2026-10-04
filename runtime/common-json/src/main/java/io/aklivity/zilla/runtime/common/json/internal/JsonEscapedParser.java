/*
 * Copyright 2021-2026 Aklivity Inc
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
import java.util.Collections;
import java.util.Iterator;

import jakarta.json.JsonException;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonStep;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Parses the string value that follows a key as a stringified JSON document, standing in for the pipeline's
 * parser from the moment a stage calls {@code JsonController#escaped()} until the scope closes. The decoded
 * chars of the string, which the wrapped parser delivers as one value or as fragments, are encoded to UTF-8 in
 * a window of their own that a nested {@link JsonParserImpl} reads, so the document is tokenized in a single
 * pass with the nested parser carrying its own state across windows and fragments. The nested document's events
 * are delivered between {@link JsonEvent#START_ESCAPED} and {@link JsonEvent#END_ESCAPED}, always structured.
 * <p>
 * Until its first pull the stand-in is transparent, so a stage that asks for the scope before the current event
 * has finished travelling downstream still reads that event from the wrapped parser. It is created, with its
 * nested parser and window, the first time a pipeline needs a scope and reused, so a pipeline that never asks
 * for one allocates nothing.
 */
final class JsonEscapedParser implements JsonParserEx
{
    private static final int INITIAL_CAPACITY = 256;
    private static final int REPLACEMENT = 0xfffd;
    private static final JsonVerbatim DRAINED = new DrainedVerbatim();

    private final JsonParserEx outer;
    private final Runnable closed;
    private final JsonParserImpl inner;
    private final UnsafeBufferEx window;

    private byte[] content;
    private int contentLength;
    private long contentTotal;
    private char carried;
    private State state;
    private boolean fragment;
    private boolean lastWindow;
    private boolean emptyContent;

    private enum State
    {
        IDLE,
        PENDING,
        OPENING,
        SCANNING
    }

    JsonEscapedParser(
        JsonParserEx outer,
        Runnable closed)
    {
        this.outer = outer;
        this.closed = closed;
        this.inner = new JsonParserImpl();
        this.content = new byte[INITIAL_CAPACITY];
        this.window = new UnsafeBufferEx(content);
        this.state = State.IDLE;
    }

    boolean pending()
    {
        return state == State.PENDING;
    }

    void open()
    {
        inner.reset();
        inner.wrap(window, 0, 0, false);
        contentLength = 0;
        contentTotal = 0L;
        carried = 0;
        fragment = false;
        lastWindow = false;
        emptyContent = false;
        state = State.PENDING;
    }

    @Override
    public JsonParserEx wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        outer.wrap(buffer, offset, limit);
        return this;
    }

    @Override
    public JsonParserEx wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        outer.wrap(buffer, offset, limit, last);
        return this;
    }

    @Override
    public void reset()
    {
        state = State.IDLE;
        outer.reset();
    }

    @Override
    public void nextDocument()
    {
        outer.nextDocument();
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public int remaining()
    {
        return outer.remaining();
    }

    @Override
    public boolean hasNext()
    {
        return hasNextEvent();
    }

    @Override
    public boolean hasNextEvent()
    {
        return state == State.SCANNING || outer.hasNextEvent();
    }

    @Override
    public Event next()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public JsonEvent nextEvent(
        Mode mode)
    {
        final JsonEvent event;
        switch (state)
        {
        case PENDING:
        case OPENING:
            event = open(outer.nextEvent(Mode.STRUCTURED));
            break;
        case SCANNING:
            event = scan();
            break;
        default:
            event = outer.nextEvent(mode);
            break;
        }
        return event;
    }

    private JsonEvent open(
        JsonEvent value)
    {
        JsonEvent event = null;
        if (value == null)
        {
            state = State.OPENING;
        }
        else if (value == JsonEvent.VALUE_STRING)
        {
            state = State.SCANNING;
            fragment = true;
            event = JsonEvent.START_ESCAPED;
        }
        else
        {
            throw new JsonException("escaped scope requires a string value, found " + value);
        }
        return event;
    }

    private JsonEvent scan()
    {
        JsonEvent event = null;
        boolean starved = false;
        while (event == null && !starved)
        {
            final JsonEvent next = emptyContent ? JsonEvent.END_DOCUMENT : inner.nextEvent(Mode.STRUCTURED);
            if (next == JsonEvent.END_DOCUMENT)
            {
                event = closeScope();
            }
            else if (next != null && next != JsonEvent.START_DOCUMENT)
            {
                event = next;
            }
            else if (next == null && lastWindow)
            {
                throw new JsonParsingException("Incomplete document in escaped string", inner.getLocation());
            }
            else if (next == null)
            {
                starved = !feed();
            }
        }
        return event;
    }

    private JsonEvent closeScope()
    {
        if (outer.identity())
        {
            // the string token the scope replaced is not a run to splice: draining it here keeps a byte-preserving
            // downstream from writing it again after the structured content of the scope
            outer.getVerbatim(Integer.MAX_VALUE);
        }
        state = State.IDLE;
        closed.run();
        return JsonEvent.END_ESCAPED;
    }

    private boolean feed()
    {
        boolean fed = fragment;
        if (!fed)
        {
            final JsonEvent next = outer.nextEvent(Mode.STRUCTURED);
            if (next == JsonEvent.VALUE_STRING)
            {
                fragment = true;
                fed = true;
            }
            else if (next != null)
            {
                throw new JsonException("escaped string continues with " + next);
            }
        }
        if (fed)
        {
            encode();
        }
        return fed;
    }

    // Moves the unconsumed tail of the nested window to the front, appends the chars of the current fragment as
    // UTF-8, and re-presents the window to the nested parser. A high surrogate cut off by the end of a non-final
    // fragment is carried to the next fragment, which completes it.
    private void encode()
    {
        final CharSequence chars = outer.getStringView();
        final boolean last = !outer.deferredBytes();
        final int tail = contentLength > 0 ? inner.remaining() : 0;
        final int capacity = tail + 3 * (chars.length() + 1);
        if (capacity > content.length)
        {
            final byte[] grown = new byte[Math.max(capacity, content.length * 2)];
            System.arraycopy(content, contentLength - tail, grown, 0, tail);
            content = grown;
        }
        else
        {
            System.arraycopy(content, contentLength - tail, content, 0, tail);
        }
        int length = tail;
        int index = 0;
        if (carried != 0)
        {
            final boolean completes = chars.length() > 0 && Character.isLowSurrogate(chars.charAt(0));
            length = encode(completes ? Character.toCodePoint(carried, chars.charAt(0)) : REPLACEMENT, length);
            index = completes ? 1 : 0;
            carried = 0;
        }
        while (index < chars.length())
        {
            final char c = chars.charAt(index);
            if (pair(chars, index))
            {
                length = encode(Character.toCodePoint(c, chars.charAt(index + 1)), length);
                index += 2;
            }
            else if (Character.isHighSurrogate(c) && index + 1 == chars.length() && !last)
            {
                carried = c;
                index++;
            }
            else
            {
                length = encode(Character.isSurrogate(c) ? REPLACEMENT : c, length);
                index++;
            }
        }
        outer.consumed(index);
        contentTotal += length - tail;
        contentLength = length;
        lastWindow = last;
        emptyContent = last && contentTotal == 0L;
        fragment = false;
        window.wrap(content, 0, length);
        inner.wrap(window, 0, length, last);
    }

    private int encode(
        int codePoint,
        int offset)
    {
        int length = offset;
        if (codePoint < 0x80)
        {
            content[length++] = (byte) codePoint;
        }
        else if (codePoint < 0x800)
        {
            content[length++] = (byte) (0xc0 | codePoint >> 6);
            content[length++] = (byte) (0x80 | codePoint & 0x3f);
        }
        else if (codePoint < 0x10000)
        {
            content[length++] = (byte) (0xe0 | codePoint >> 12);
            content[length++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
            content[length++] = (byte) (0x80 | codePoint & 0x3f);
        }
        else
        {
            content[length++] = (byte) (0xf0 | codePoint >> 18);
            content[length++] = (byte) (0x80 | codePoint >> 12 & 0x3f);
            content[length++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
            content[length++] = (byte) (0x80 | codePoint & 0x3f);
        }
        return length;
    }

    private static boolean pair(
        CharSequence chars,
        int index)
    {
        return Character.isHighSurrogate(chars.charAt(index)) &&
            index + 1 < chars.length() &&
            Character.isLowSurrogate(chars.charAt(index + 1));
    }

    @Override
    public String getString()
    {
        return source().getString();
    }

    @Override
    public CharSequence getStringView()
    {
        return source().getStringView();
    }

    @Override
    public boolean isIntegralNumber()
    {
        return source().isIntegralNumber();
    }

    @Override
    public int getInt()
    {
        return source().getInt();
    }

    @Override
    public long getLong()
    {
        return source().getLong();
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return source().getBigDecimal();
    }

    @Override
    public JsonLocation getLocation()
    {
        return source().getLocation();
    }

    @Override
    public DirectBufferEx getSegment()
    {
        if (state == State.SCANNING)
        {
            throw new UnsupportedOperationException("segments are not delivered within an escaped scope");
        }
        return outer.getSegment();
    }

    @Override
    public JsonVerbatim getVerbatim(
        int limit)
    {
        return state == State.SCANNING ? DRAINED : outer.getVerbatim(limit);
    }

    @Override
    public void skipValue()
    {
        source().skipValue();
    }

    @Override
    public boolean deferredBytes()
    {
        return source().deferredBytes();
    }

    @Override
    public void consumed(
        int sourceUnits)
    {
        source().consumed(sourceUnits);
    }

    @Override
    public void close()
    {
    }

    private JsonParserEx source()
    {
        return state == State.SCANNING ? inner : outer;
    }

    // a run with nothing left to pull: the bytes of an escaped scope are the escaped form of its content, never
    // original source bytes to splice
    private static final class DrainedVerbatim implements JsonVerbatim
    {
        private final UnsafeBufferEx empty = new UnsafeBufferEx(new byte[0]);

        @Override
        public Iterator<JsonStep> getSteps()
        {
            return Collections.emptyIterator();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            return empty;
        }
    }
}
