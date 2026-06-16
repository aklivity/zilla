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

import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Backs {@link JsonPipeline}: holds the bound root {@link JsonSink} and the {@link JsonParserEx}
 * driver. {@link #feed(DirectBuffer, int, int)} re-targets the parser at the frame buffer then pumps
 * each parsed event through the root sink, passing the parser itself as the immutable
 * {@code JsonSource} view and the {@link JsonController}.
 */
public final class JsonPipelineImpl implements JsonPipeline
{
    private final JsonParserEx parser;
    private final JsonSource source;
    private final JsonController control;
    private final JsonSink root;

    private boolean suspended;
    // the value event in flight across a suspend, handed to root.resume() so no stage stores it
    private JsonEvent resumeEvent;

    public JsonPipelineImpl(
        JsonParserEx parser,
        JsonSink root)
    {
        this.parser = parser;
        // the parser is also the per-event source view and the upstream controller a stage steers
        this.source = (JsonSource) parser;
        this.control = (JsonController) parser;
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
    public long position()
    {
        return parser.position();
    }

    @Override
    public Status feed(
        DirectBuffer buffer,
        int offset,
        int length,
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
                parser.wrap(buffer, offset, length, last);
            }
            while (status == Status.ADVANCED && parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
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
}
