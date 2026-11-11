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

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonTokenizer;

// A tokenizer that reads documents and nothing more, as one that has no notion of an escaped scope does
final class PlainJsonTokenizer implements JsonTokenizer
{
    private final JsonTokenizer delegate;

    PlainJsonTokenizer(
        JsonTokenizer delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void reset()
    {
        delegate.reset();
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        delegate.wrap(buffer, offset, limit, last);
    }

    @Override
    public void window(
        int length)
    {
        delegate.window(length);
    }

    @Override
    public boolean advance()
    {
        return delegate.advance();
    }

    @Override
    public JsonParser.Event event()
    {
        return delegate.event();
    }

    @Override
    public void clearEvent()
    {
        delegate.clearEvent();
    }

    @Override
    public String stringValue()
    {
        return delegate.stringValue();
    }

    @Override
    public CharSequence stringView()
    {
        return delegate.stringView();
    }

    @Override
    public void markScratchConsumed(
        int consumed)
    {
        delegate.markScratchConsumed(consumed);
    }

    @Override
    public void positionAfter(
        int consumed)
    {
        delegate.positionAfter(consumed);
    }

    @Override
    public boolean declined()
    {
        return delegate.declined();
    }

    @Override
    public void acceptDeclined()
    {
        delegate.acceptDeclined();
    }

    @Override
    public long streamOffset()
    {
        return delegate.streamOffset();
    }

    @Override
    public long line()
    {
        return delegate.line();
    }

    @Override
    public long column()
    {
        return delegate.column();
    }

    @Override
    public long valueStreamStart()
    {
        return delegate.valueStreamStart();
    }

    @Override
    public long valueStreamEnd()
    {
        return delegate.valueStreamEnd();
    }

    @Override
    public boolean done()
    {
        return delegate.done();
    }

    @Override
    public void skip(
        long streamOffset,
        long line,
        long column)
    {
        delegate.skip(streamOffset, line, column);
    }

    @Override
    public long documentEndOffset()
    {
        return delegate.documentEndOffset();
    }

    @Override
    public boolean inObjectContext()
    {
        return delegate.inObjectContext();
    }

    @Override
    public boolean inArrayContext()
    {
        return delegate.inArrayContext();
    }

    @Override
    public boolean memberSeparated()
    {
        return delegate.memberSeparated();
    }

    @Override
    public boolean separatorBefore()
    {
        return delegate.separatorBefore();
    }

    @Override
    public boolean fragmenting()
    {
        return delegate.fragmenting();
    }

    @Override
    public boolean stringVerbatim()
    {
        return delegate.stringVerbatim();
    }

    @Override
    public void segmenting(
        boolean segmenting)
    {
        delegate.segmenting(segmenting);
    }

    @Override
    public void scalarSegment(
        boolean scalarSegment)
    {
        delegate.scalarSegment(scalarSegment);
    }

    @Override
    public void nextDocument()
    {
        delegate.nextDocument();
    }

    @Override
    public void mark()
    {
        delegate.mark();
    }

    @Override
    public void rewind()
    {
        delegate.rewind();
    }

    @Override
    public void rewindKey(
        long priorEnd,
        long priorLine,
        long priorColumn,
        long floor)
    {
        delegate.rewindKey(priorEnd, priorLine, priorColumn, floor);
    }

    @Override
    public void rewindLookahead(
        long offset,
        long line,
        long column)
    {
        delegate.rewindLookahead(offset, line, column);
    }
}
