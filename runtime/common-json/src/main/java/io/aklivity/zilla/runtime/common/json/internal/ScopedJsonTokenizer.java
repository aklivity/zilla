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

import jakarta.json.JsonException;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonTokenizer;

// Gives a tokenizer that cannot read an escaped scope the ability to, without it knowing anything of scopes. The
// plain tokenizer reads the document up to the key that precedes the string, and the scope is then read in place, from
// the same window, by a reference tokenizer standing in for it until the closing quote, when the plain tokenizer is
// told the value was consumed and carries on from there.
final class ScopedJsonTokenizer implements JsonScopeTokenizer
{
    private final JsonTokenizer outer;
    private final boolean quoted;
    private JsonTokenizerImpl inner;

    private boolean armed;
    private boolean scoped;
    private boolean markScoped;

    private DirectBufferEx buffer;
    private int offset;
    private int limit;
    private boolean last;
    private long windowStream;

    ScopedJsonTokenizer(
        JsonTokenizer outer,
        boolean quoted)
    {
        this.outer = outer;
        this.quoted = quoted;
    }

    @Override
    public void escape()
    {
        if (scoped)
        {
            throw new JsonException("escaped scope is already open");
        }

        armed = true;
    }

    @Override
    public boolean pending()
    {
        return scoped ? inner.pending() : outer.event() != null;
    }

    @Override
    public JsonEvent marker()
    {
        return scoped ? inner.marker() : null;
    }

    @Override
    public void clearMarker()
    {
        if (scoped)
        {
            final JsonEvent marker = inner.marker();

            inner.clearMarker();

            if (marker == JsonEvent.END_ESCAPED)
            {
                leave();
            }
        }
    }

    @Override
    public boolean inScope()
    {
        return scoped && inner.inScope();
    }

    @Override
    public void reset()
    {
        armed = false;
        scoped = false;
        outer.reset();

        if (inner != null)
        {
            inner.reset();
        }
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = limit;
        this.last = last;

        final JsonTokenizer active = active();
        this.windowStream = active.streamOffset();
        active.wrap(buffer, offset, limit, last);
    }

    @Override
    public void window(
        int length)
    {
        active().window(length);
    }

    @Override
    public boolean advance()
    {
        if (armed && !scoped)
        {
            enter();
        }

        return active().advance();
    }

    @Override
    public JsonParser.Event event()
    {
        return active().event();
    }

    @Override
    public void clearEvent()
    {
        active().clearEvent();
    }

    @Override
    public String stringValue()
    {
        return active().stringValue();
    }

    @Override
    public CharSequence stringView()
    {
        return active().stringView();
    }

    @Override
    public void markScratchConsumed(
        int consumed)
    {
        active().markScratchConsumed(consumed);
    }

    @Override
    public void positionAfter(
        int consumed)
    {
        active().positionAfter(consumed);
    }

    @Override
    public boolean declined()
    {
        return active().declined();
    }

    @Override
    public void acceptDeclined()
    {
        active().acceptDeclined();
    }

    @Override
    public long streamOffset()
    {
        return active().streamOffset();
    }

    @Override
    public long line()
    {
        return active().line();
    }

    @Override
    public long column()
    {
        return active().column();
    }

    @Override
    public long valueStreamStart()
    {
        return active().valueStreamStart();
    }

    @Override
    public long valueStreamEnd()
    {
        return active().valueStreamEnd();
    }

    @Override
    public boolean done()
    {
        return outer.done();
    }

    @Override
    public void skip(
        long streamOffset,
        long line,
        long column)
    {
        outer.skip(streamOffset, line, column);
    }

    @Override
    public long documentEndOffset()
    {
        return outer.documentEndOffset();
    }

    @Override
    public boolean inObjectContext()
    {
        return active().inObjectContext();
    }

    @Override
    public boolean inArrayContext()
    {
        return active().inArrayContext();
    }

    @Override
    public boolean memberSeparated()
    {
        return active().memberSeparated();
    }

    @Override
    public boolean separatorBefore()
    {
        return active().separatorBefore();
    }

    @Override
    public boolean fragmenting()
    {
        return active().fragmenting();
    }

    @Override
    public boolean stringVerbatim()
    {
        return active().stringVerbatim();
    }

    @Override
    public void segmenting(
        boolean segmenting)
    {
        active().segmenting(segmenting);
    }

    @Override
    public void scalarSegment(
        boolean scalarSegment)
    {
        active().scalarSegment(scalarSegment);
    }

    @Override
    public void nextDocument()
    {
        outer.nextDocument();
    }

    @Override
    public void mark()
    {
        markScoped = scoped;
        active().mark();
    }

    @Override
    public void rewind()
    {
        if (markScoped && !scoped)
        {
            throw new IllegalStateException("rewind requires the scope the mark was taken in");
        }

        if (scoped && !markScoped)
        {
            scoped = false;
        }

        active().rewind();
    }

    @Override
    public void rewindKey(
        long priorEnd,
        long priorLine,
        long priorColumn,
        long floor)
    {
        active().rewindKey(priorEnd, priorLine, priorColumn, floor);
    }

    @Override
    public void rewindLookahead(
        long offset,
        long line,
        long column)
    {
        active().rewindLookahead(offset, line, column);
    }

    private JsonTokenizer active()
    {
        return scoped ? inner : outer;
    }

    private void enter()
    {
        if (inner == null)
        {
            inner = new JsonTokenizerImpl(quoted);
        }

        final long streamOffset = outer.streamOffset();
        final int position = offset + (int) (streamOffset - windowStream);

        armed = false;
        scoped = true;
        inner.scope(buffer, position, limit, last, streamOffset, outer.line(), outer.column(), quoted);
        inner.window(limit - position);
    }

    private void leave()
    {
        final long streamOffset = inner.streamOffset();
        final int position = offset + (int) (streamOffset - windowStream);

        scoped = false;
        outer.skip(streamOffset, inner.line(), inner.column());
        outer.wrap(buffer, position, limit, last);
        outer.window(limit - position);
    }
}
