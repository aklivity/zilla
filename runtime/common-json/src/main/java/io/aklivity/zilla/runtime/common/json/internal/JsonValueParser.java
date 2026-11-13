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

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

/**
 * Presents the next value of a parser that is already running, a container or a scalar, as a document of its
 * own, so a pipeline can be driven over part of a stream without reading those bytes a second time.
 * <p>
 * The events are those of the wrapped parser, between a {@link JsonEvent#START_DOCUMENT} and a
 * {@link JsonEvent#END_DOCUMENT} that are made up here, and there are no more once the value closes, leaving the
 * wrapped parser positioned right after it. The wrapped parser keeps everything else: the window it reads, the
 * bytes it has not consumed and presents again, and the pushback of a fragment that a stage does not take. So
 * {@code wrap} does nothing, the owner of the wrapped parser having wrapped it already, and {@code reset}
 * re-arms the document without resetting the wrapped parser.
 */
public final class JsonValueParser implements JsonParserEx
{
    private enum State
    {
        PENDING,
        BODY,
        CLOSING,
        DONE
    }

    private final JsonParserEx parser;

    private State state;
    private int depth;
    private JsonEvent lastEvent;

    public JsonValueParser(
        JsonParserEx parser)
    {
        this.parser = parser;
        this.state = State.PENDING;
    }

    @Override
    public void escaped()
    {
        parser.escaped();
    }

    @Override
    public JsonParserEx wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        return this;
    }

    @Override
    public JsonParserEx wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        return this;
    }

    @Override
    public void reset()
    {
        state = State.PENDING;
        depth = 0;
        lastEvent = null;
    }

    @Override
    public int save(
        MutableDirectBufferEx buffer,
        int offset)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restore(
        DirectBufferEx buffer,
        int offset)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void nextDocument()
    {
        reset();
    }

    @Override
    public boolean identity()
    {
        return parser.identity();
    }

    @Override
    public boolean deterministic()
    {
        return parser.deterministic();
    }

    @Override
    public int remaining()
    {
        return parser.remaining();
    }

    @Override
    public boolean hasNext()
    {
        return hasNextEvent();
    }

    @Override
    public boolean hasNextEvent()
    {
        return switch (state)
        {
        case PENDING, BODY -> parser.hasNextEvent();
        case CLOSING -> true;
        case DONE -> false;
        };
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
        JsonEvent event = null;

        switch (state)
        {
        case PENDING:
            if (parser.hasNextEvent())
            {
                state = State.BODY;
                event = JsonEvent.START_DOCUMENT;
            }
            break;
        case BODY:
            event = parser.nextEvent(mode);

            if (event != null)
            {
                onBodyEvent(event);
            }
            break;
        case CLOSING:
            state = State.DONE;
            event = JsonEvent.END_DOCUMENT;
            break;
        default:
            break;
        }

        lastEvent = event != null ? event : lastEvent;

        return event;
    }

    @Override
    public String getString()
    {
        return parser.getString();
    }

    @Override
    public JsonStringView getStringView()
    {
        return parser.getStringView();
    }

    @Override
    public boolean isIntegralNumber()
    {
        return parser.isIntegralNumber();
    }

    @Override
    public int getInt()
    {
        return parser.getInt();
    }

    @Override
    public long getLong()
    {
        return parser.getLong();
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return parser.getBigDecimal();
    }

    @Override
    public JsonLocation getLocation()
    {
        return parser.getLocation();
    }

    @Override
    public DirectBufferEx getSegment()
    {
        return parser.getSegment();
    }

    @Override
    public JsonVerbatim getVerbatim(
        int limit)
    {
        return parser.getVerbatim(limit);
    }

    // skipping a container takes its close with it, which the document is not told, so it closes with the skip
    @Override
    public void skipValue()
    {
        parser.skipValue();

        if (lastEvent == JsonEvent.START_OBJECT || lastEvent == JsonEvent.START_ARRAY)
        {
            depth--;
            state = depth == 0 ? State.CLOSING : state;
        }
    }

    @Override
    public void verbatim()
    {
        parser.verbatim();
    }

    @Override
    public boolean deferredBytes()
    {
        return parser.deferredBytes();
    }

    @Override
    public void consumed(
        int sourceUnits)
    {
        parser.consumed(sourceUnits);
    }

    @Override
    public void close()
    {
    }

    private void onBodyEvent(
        JsonEvent event)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        default:
            break;
        }

        if (depth == 0 && !parser.deferredBytes())
        {
            state = State.CLOSING;
        }
    }
}
