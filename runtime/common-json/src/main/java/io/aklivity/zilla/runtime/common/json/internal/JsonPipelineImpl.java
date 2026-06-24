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

import jakarta.json.JsonException;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonValidationException;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Backs {@link JsonPipeline}: holds the bound root {@link JsonSink} and the {@link JsonParserEx}
 * driver. {@link #transform(DirectBuffer, int, int)} re-targets the parser at the frame buffer then pumps
 * each parsed event through the root sink, passing a {@link Source} view that adapts the parser to the
 * immutable {@code JsonSource} surface and a {@link Control} that adapts it to the {@link JsonController}.
 */
public final class JsonPipelineImpl implements JsonPipeline
{
    private final JsonParserEx parser;
    private final Source source;
    private final Control control;
    private final JsonSink root;
    private final JsonReporter reporter;
    private final Diagnostic diagnostic;
    // the terminal generator the pipeline re-targets per transform, or null for a non-generator terminal
    private final JsonGeneratorEx generator;
    private final JsonPipelineResult result;
    private final boolean lenient;

    private boolean suspended;
    // the value event in flight across a suspend, handed to root.resume() so no stage stores it
    private JsonEvent resumeEvent;

    public JsonPipelineImpl(
        JsonParserEx parser,
        JsonSink root,
        JsonReporter reporter,
        JsonGeneratorEx generator,
        boolean lenient)
    {
        this.parser = parser;
        // the source view and the upstream controller a stage steers are adapters over the parser surface
        this.source = new Source(parser);
        this.control = new Control(parser);
        this.root = root;
        this.reporter = reporter;
        this.diagnostic = new Diagnostic();
        this.generator = generator;
        this.lenient = lenient;
        this.result = new JsonPipelineResult();
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
        suspended = false;
        diagnostic.message = null;
        resumeEvent = null;
    }

    @Override
    public boolean identity()
    {
        return parser.identity() && root.identity();
    }

    @Override
    public int remaining()
    {
        return parser.remaining();
    }

    @Override
    public Status transform(
        DirectBuffer buffer,
        int offset,
        int limit,
        boolean last)
    {
        Status status = Status.ADVANCED;
        try
        {
            boolean resumingDocumentEnd = false;
            if (suspended)
            {
                resumingDocumentEnd = resumeEvent == JsonEvent.END_DOCUMENT;
                status = root.resume(control, source, resumeEvent);
            }
            else
            {
                parser.wrap(buffer, offset, limit, last);
            }
            if (resumingDocumentEnd && status == Status.ADVANCED)
            {
                status = Status.COMPLETED;
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
                status = root.transform(control, source, event);
                if (status == Status.SUSPENDED)
                {
                    // the pump owns the resume cursor: remember the in-flight event for the next entry
                    resumeEvent = event;
                }
            }
            if (status == Status.COMPLETED)
            {
                status = completeDocument(status);
            }
            if (status == Status.ADVANCED || status == Status.STARVED)
            {
                // the window was consumed before a terminal value — either the pump ran out of events, or a
                // stage starved mid-value (e.g. a validator declining a fragment to accumulate it). Always
                // give the sink a final drain before the window is replaced: a verbatim run pulls the bytes it
                // has consumed so far via getVerbatim, so its cursor tracks the parse frontier across windows
                // and never lags into a replaced window. STARVED stays STARVED; an exhausted-but-advancing
                // pump needs more input (or is truncated on the final window).
                final Status drained = root.flush(control, source);
                if (drained == Status.SUSPENDED)
                {
                    status = Status.SUSPENDED;
                }
                else if (status == Status.ADVANCED)
                {
                    status = last ? Status.REJECTED : Status.STARVED;
                }
            }
        }
        catch (JsonValidationException ex)
        {
            diagnostic.message = ex.getMessage();
            if (reporter != null)
            {
                reporter.rejected(diagnostic);
            }
            status = lenient ? Status.COMPLETED : Status.REJECTED;
        }
        catch (JsonParsingException ex)
        {
            status = Status.REJECTED;
            diagnostic.message = ex.getMessage();
        }
        catch (JsonException ex)
        {
            status = Status.REJECTED;
            diagnostic.message = ex.getMessage();
        }
        if (status == Status.REJECTED && reporter != null)
        {
            // terminal failure only — never STARVED/SUSPENDED back-pressure; the diagnostic is a reused,
            // call-scoped view, so the reporter must copy out anything it needs before returning
            reporter.rejected(diagnostic);
        }
        suspended = status == Status.SUSPENDED;
        return status;
    }

    @Override
    public JsonPipelineResult transform(
        DirectBuffer src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBuffer dst,
        int dstOffset,
        int dstLimit)
    {
        // re-target the terminal generator at the caller's output region, preserving structural context
        // across a SUSPENDED drain, then pump the same window the existing transform contract expects
        generator.wrap(dst, dstOffset, dstLimit);
        Status status = transform(src, offset, limit, last);
        boolean rejected = status == Status.REJECTED;
        int produced = rejected ? 0 : generator.length();
        // SUSPENDED holds the input steady (drain and re-present the same window); otherwise the window
        // advanced by all but the unconsumed tail the caller re-presents at the front of the next window
        int consumed = rejected || status == Status.SUSPENDED ? 0 : (limit - offset) - parser.remaining();
        return result.set(status, consumed, produced);
    }

    private Status completeDocument(
        Status completed)
    {
        Status status = completed;
        JsonEvent event = parser.nextEvent(control.mode());
        while (event != null && status == Status.COMPLETED)
        {
            status = root.transform(control, source, event);
            if (status == Status.SUSPENDED)
            {
                resumeEvent = event;
            }
            else
            {
                status = Status.COMPLETED;
                event = parser.nextEvent(control.mode());
            }
        }
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
        public JsonVerbatim getVerbatim(
            int limit)
        {
            return parser.getVerbatim(limit);
        }

        @Override
        public void skipValue()
        {
            parser.skipValue();
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

    // the reused, call-scoped diagnostic pushed to the reporter: its message is whatever the rejecting
    // component populated — here the parser's caught parsing exception, or null for a truncated value
    private static final class Diagnostic implements JsonDiagnostic
    {
        private String message;

        @Override
        public String message()
        {
            return message;
        }
    }
}
