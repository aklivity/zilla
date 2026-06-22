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
package io.aklivity.zilla.runtime.common.avro.internal;

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.ADVANCED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.STARVED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.SUSPENDED;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroDiagnostic;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipelineResult;
import io.aklivity.zilla.runtime.common.avro.AvroReporter;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * Backs {@link AvroPipeline}: a thin pump that re-targets the {@link AvroParser} cursor at the frame
 * buffer and pushes each pulled event through the stage chain to the terminal sink. It owns the
 * per-edge handles the stages see — a read-only {@link AvroSource} view that delegates to the parser's
 * accessors (without exposing the cursor, so a stage cannot disturb the pump) and an
 * {@link AvroController} that forwards a stage's segment request — leaving the parser a pure cursor.
 * <p>
 * The status is whatever the sink reports — completion is the sink's to signal; malformed binary aborts
 * with {@code REJECTED}. On {@code SUSPENDED} (bounded output full) the parser keeps its position, so a
 * resume {@code feed} continues from where it paused — its buffer arguments are ignored.
 */
final class AvroPipelineImpl implements AvroPipeline
{
    private final AvroParser parser;
    private final Source source;
    private final Control control;
    private final AvroSink root;
    private final AvroReporter reporter;
    private final Diagnostic diagnostic;
    // the terminal generator the pipeline re-targets per transform, or null for a non-generator terminal
    private final AvroGenerator generator;
    private final AvroPipelineResult result;

    private boolean suspended;
    private AvroEvent suspendedEvent;

    AvroPipelineImpl(
        AvroParser parser,
        AvroSink root,
        AvroReporter reporter,
        AvroGenerator generator)
    {
        this.parser = parser;
        this.source = new Source(parser);
        this.control = new Control(parser);
        this.root = root;
        this.reporter = reporter;
        this.diagnostic = new Diagnostic();
        this.generator = generator;
        this.result = new AvroPipelineResult();
    }

    @Override
    public void reset()
    {
        parser.reset();
        root.reset();
        suspended = false;
        diagnostic.message = null;
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
        Status status = ADVANCED;
        AvroEvent event = null;
        try
        {
            if (suspended)
            {
                status = root.resume(control, source, suspendedEvent);
            }
            else
            {
                parser.wrap(buffer, offset, limit, last);
            }
            while (status == ADVANCED)
            {
                event = parser.nextEvent(control.mode());
                if (event == null)
                {
                    break;
                }
                status = root.transform(control, source, event);
            }
            if (status == ADVANCED)
            {
                // the input window was consumed without completing the datum (last == false, else the
                // parser would have thrown for truncation) — the pipeline needs the next window
                status = STARVED;
            }
        }
        catch (AvroValidationException ex)
        {
            status = REJECTED;
            diagnostic.message = ex.getMessage();
        }
        if (status == REJECTED && reporter != null)
        {
            // terminal failure only — never STARVED/SUSPENDED back-pressure; the diagnostic is a reused,
            // call-scoped view, so the reporter must copy out anything it needs before returning
            reporter.rejected(diagnostic);
        }
        suspended = status == SUSPENDED;
        // the pump remembers which event suspended (so the sink keeps no resume state); a re-suspend on
        // resume leaves event null, keeping the prior suspendedEvent
        if (suspended && event != null)
        {
            suspendedEvent = event;
        }
        return status;
    }

    @Override
    public AvroPipelineResult transform(
        DirectBuffer src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBuffer dst,
        int dstOffset,
        int dstLimit)
    {
        // re-target the terminal generator at the caller's output region, preserving structural context
        // across a SUSPENDED drain, then pump the same window the existing feed contract expects
        generator.wrap(dst, dstOffset, dstLimit);
        Status status = transform(src, offset, limit, last);
        boolean rejected = status == REJECTED;
        int produced = rejected ? 0 : generator.length();
        // SUSPENDED holds the input steady (drain and re-present the same window); otherwise the window
        // advanced by all but the unconsumed tail the caller re-presents at the front of the next window
        int consumed = rejected || status == SUSPENDED ? 0 : (limit - offset) - parser.remaining();
        return result.set(status, consumed, produced);
    }

    // the read-only view handed to stages: the parser's accessors without its cursor, so a stage reads the
    // current value but cannot advance the pump
    private static final class Source implements AvroSource
    {
        private final AvroParser parser;

        private Source(
            AvroParser parser)
        {
            this.parser = parser;
        }

        @Override
        public boolean getBoolean()
        {
            return parser.getBoolean();
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
        public float getFloat()
        {
            return parser.getFloat();
        }

        @Override
        public double getDouble()
        {
            return parser.getDouble();
        }

        @Override
        public String getString()
        {
            return parser.getString();
        }

        @Override
        public String getField()
        {
            return parser.getField();
        }

        @Override
        public String getKey()
        {
            return parser.getKey();
        }

        @Override
        public DirectBuffer getSegment()
        {
            return parser.getSegment();
        }

        @Override
        public int deferredBytes()
        {
            return parser.deferredBytes();
        }

        @Override
        public AvroType type()
        {
            return parser.type();
        }

        @Override
        public AvroLocation getLocation()
        {
            return parser.getLocation();
        }
    }

    // the head edge's controller: a stage's segmentable() request becomes a one-shot SEGMENTED mode that the
    // pump passes to the parser on the next pull; consumed() pushback advances the parser's value cursor so
    // the terminal sink resumes a bounded value without keeping its own offset
    private static final class Control implements AvroController
    {
        private final AvroParser parser;

        private boolean segmented;

        private Control(
            AvroParser parser)
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

        private AvroParser.Mode mode()
        {
            AvroParser.Mode mode = segmented ? AvroParser.Mode.SEGMENTED : AvroParser.Mode.STRUCTURED;
            segmented = false;
            return mode;
        }
    }

    // the reused, call-scoped diagnostic pushed to the reporter: its message is whatever the rejecting
    // component populated — here the parser's caught validation exception
    private static final class Diagnostic implements AvroDiagnostic
    {
        private String message;

        @Override
        public String message()
        {
            return message;
        }
    }
}
