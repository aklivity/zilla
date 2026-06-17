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

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Backs {@link JsonPipeline}: holds the bound root {@link JsonSink} and the {@link JsonParserEx}
 * driver. {@link #feed(DirectBuffer, int, int)} re-targets the parser at the frame buffer then pumps
 * each parsed event through the root sink, passing a {@link Source} view that adapts the parser to the
 * immutable {@code JsonSource} surface and a {@link Control} that adapts it to the {@link JsonController}.
 */
public final class JsonPipelineImpl implements JsonPipeline
{
    private final JsonParserEx parser;
    private final Source source;
    private final Control control;
    private final JsonSink root;

    private boolean suspended;
    // the value event in flight across a suspend, handed to root.resume() so no stage stores it
    private JsonEvent resumeEvent;

    public JsonPipelineImpl(
        JsonParserEx parser,
        JsonSink root)
    {
        this.parser = parser;
        // the source view and the upstream controller a stage steers are adapters over the parser surface
        this.source = new Source(parser);
        this.control = new Control(parser);
        this.root = root;
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
        suspended = false;
        resumeEvent = null;
    }

    @Override
    public int remaining()
    {
        return parser.remaining();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        Status status = Status.ADVANCED;
        try
        {
            if (suspended)
            {
                status = root.resume(control, source, resumeEvent);
            }
            else
            {
                parser.wrap(buffer, offset, limit, last);
            }
            while (status == Status.ADVANCED)
            {
                // pull with the requested mode before any tokenizer advance, so a segmentable() request lands
                // on the just-delivered boundary; a null event means the window is consumed (no hasNextEvent()
                // gate here, which would advance the tokenizer past the boundary before the mode is applied)
                final JsonEvent event = parser.nextEvent(control.mode());
                if (event == null)
                {
                    break;
                }
                status = root.feed(control, source, event);
                if (status == Status.SUSPENDED)
                {
                    // the pump owns the resume cursor: remember the in-flight event for the next entry
                    resumeEvent = event;
                }
            }
            if (status == Status.ADVANCED)
            {
                // the window was consumed before the value completed: more input is needed, unless this was
                // the final window, in which case the value is truncated
                status = last ? Status.REJECTED : Status.STARVED;
            }
        }
        catch (JsonParsingException ex)
        {
            status = Status.REJECTED;
        }
        suspended = status == Status.SUSPENDED;
        return status;
    }

    // Adapts the parser to the non-advancing JsonSource view a stage reads off the current event.
    private static final class Source implements JsonSource
    {
        private final JsonParserEx parser;

        private Source(
            JsonParserEx parser)
        {
            this.parser = parser;
        }

        @Override
        public String getString()
        {
            return parser.getString();
        }

        @Override
        public CharSequence getStringView()
        {
            return parser.getStringView();
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            return parser.getBigDecimal();
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
        public JsonLocation getLocation()
        {
            return parser.getLocation();
        }

        @Override
        public DirectBuffer getSegment()
        {
            return parser.getSegment();
        }

        @Override
        public boolean deferredBytes()
        {
            return parser.deferredBytes();
        }
    }

    // Adapts the parser to the JsonController a stage uses to steer its immediate upstream: a segmentable()
    // request becomes a one-shot SEGMENTED mode the pump threads into the parser's next pull, so the segment
    // request need not narrow onto the parser surface; consumed() pushback forwards to the parser's cursor.
    private static final class Control implements JsonController
    {
        private final JsonParserEx parser;

        private boolean segmented;

        private Control(
            JsonParserEx parser)
        {
            this.parser = parser;
        }

        @Override
        public void segmentable()
        {
            segmented = true;
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            parser.consumed(sourceBytes);
        }

        private Mode mode()
        {
            Mode mode = segmented ? Mode.SEGMENTED : Mode.STRUCTURED;
            segmented = false;
            return mode;
        }
    }
}
