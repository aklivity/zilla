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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufDiagnostic;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * The runnable pipeline: a thin pump that re-targets the {@link ProtobufParser} cursor at the frame
 * buffer and feeds each pulled {@link ProtobufEvent} through the stage chain to the terminal sink. It
 * owns the per-edge handles the stages receive — a {@link ProtobufSource} view that delegates to the
 * parser's accessors (without exposing the cursor, so a stage cannot disturb the pump) and a
 * {@link ProtobufController} that records segment requests — leaving the parser as a pure cursor.
 */
public final class ProtobufPipelineImpl implements ProtobufPipeline
{
    private final ProtobufParser parser;
    private final Source source;
    private final Control control;
    private final ProtobufSink head;
    private final ProtobufReporter reporter;
    private final Diagnostic diagnostic;
    // the terminal generator the pipeline re-targets per transform, or null for a non-generator terminal
    private final ProtobufGenerator generator;
    private final ProtobufPipelineResult result;

    private boolean suspended;
    private boolean starved;
    // the event in flight across an output suspend, handed to head.resume() so no sink stores it
    private ProtobufEvent resumeEvent;

    public ProtobufPipelineImpl(
        ProtobufParser parser,
        List<ProtobufTransform> transforms,
        ProtobufSink sink,
        ProtobufReporter reporter,
        ProtobufGenerator generator)
    {
        this.parser = parser;
        this.reporter = reporter;
        this.diagnostic = new Diagnostic();
        this.generator = generator;
        this.result = new ProtobufPipelineResult();
        // the per-edge handles the stages see: a read-only source view of the parser, and a control handle
        // that records a stage's segment request which the pump turns into the SEGMENTED mode on the next pull
        this.source = new Source(parser);
        this.control = new Control();

        ProtobufSink chain = sink;
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            chain = new StageSink(transforms.get(i), chain);
        }
        this.head = chain;
    }

    @Override
    public void reset()
    {
        head.reset();
        suspended = false;
        starved = false;
        diagnostic.message = null;
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
                // output back-pressure: continue the suspended work on the same window without replaying it
                status = head.resume(control, source, resumeEvent);
            }
            else if (starved)
            {
                // input back-pressure: continue the in-flight message with the next window
                parser.resume(buffer, offset, limit, last);
            }
            else
            {
                parser.wrap(buffer, offset, limit, last);
            }
            while (status == Status.ADVANCED && parser.hasNext())
            {
                ProtobufEvent event = parser.nextEvent(control.mode());
                if (event == null)
                {
                    status = Status.STARVED;
                }
                else
                {
                    status = head.feed(control, source, event);
                    if (status == Status.SUSPENDED)
                    {
                        // the pump owns the resume cursor: remember the in-flight event for the next entry
                        resumeEvent = event;
                    }
                    // a sink STARVED leaves the unconsumed tail uncommitted (consumed() advanced the cursor by
                    // only what the sink took), so remaining() already reports it for the next window — no rewind
                }
            }
            suspended = status == Status.SUSPENDED;
            starved = status == Status.STARVED;
        }
        catch (ProtobufException ex)
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
        return status;
    }

    @Override
    public ProtobufPipelineResult transform(
        DirectBuffer src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBuffer dst,
        int dstOffset,
        int dstLimit)
    {
        // re-target the terminal generator at the caller's output region, preserving structural context
        // across a SUSPENDED drain, then pump the same window the existing feed contract expects; the
        // protobuf generator bounds writes by a length from the offset, so pass dstLimit - dstOffset
        generator.wrap(dst, dstOffset, dstLimit - dstOffset);
        Status status = feed(src, offset, limit, last);
        boolean rejected = status == Status.REJECTED;
        if (status == Status.COMPLETED)
        {
            // finalize the document: the wire generator's records are already closed (a no-op at completion),
            // while the JSON generator writes its closing structure here so the produced bytes are well-formed
            generator.flush();
        }
        int produced = rejected ? 0 : generator.length();
        // SUSPENDED holds the input steady (drain and re-present the same window); otherwise the window
        // advanced by all but the unconsumed tail the caller re-presents at the front of the next window
        int consumed = rejected || status == Status.SUSPENDED ? 0 : (limit - offset) - parser.remaining();
        return result.set(status, consumed, produced);
    }

    private final class StageSink implements ProtobufSink
    {
        private final ProtobufTransform transform;
        private final ProtobufSink downstream;

        private StageSink(
            ProtobufTransform transform,
            ProtobufSink downstream)
        {
            this.transform = transform;
            this.downstream = downstream;
        }

        @Override
        public Status feed(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            return transform.feed(control, source, event, downstream);
        }

        @Override
        public Status resume(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            return transform.resume(control, source, event, downstream);
        }

        @Override
        public void reset()
        {
            transform.reset();
            downstream.reset();
        }
    }

    // the read-only view handed to stages: the parser's accessors without its cursor, so a stage reads the
    // current value but cannot advance the pump
    private static final class Source implements ProtobufSource
    {
        private final ProtobufParser parser;

        private Source(
            ProtobufParser parser)
        {
            this.parser = parser;
        }

        @Override
        public ProtobufField field()
        {
            return parser.field();
        }

        @Override
        public ProtobufMessage message()
        {
            return parser.message();
        }

        @Override
        public int fieldNumber()
        {
            return parser.fieldNumber();
        }

        @Override
        public ProtobufWireType wireType()
        {
            return parser.wireType();
        }

        @Override
        public long longValue()
        {
            return parser.longValue();
        }

        @Override
        public double doubleValue()
        {
            return parser.doubleValue();
        }

        @Override
        public float floatValue()
        {
            return parser.floatValue();
        }

        @Override
        public DirectBuffer segment()
        {
            return parser.segment();
        }

        @Override
        public int deferredBytes()
        {
            return parser.deferredBytes();
        }
    }

    // the head edge's controller: a stage's segmentable() request becomes a one-shot SEGMENTED mode that the
    // pump passes to the parser on the next pull, and a sink's consumed() pushback advances the parser's slice
    private final class Control implements ProtobufController
    {
        private boolean segmented;

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

        private ProtobufParser.Mode mode()
        {
            ProtobufParser.Mode mode = segmented ? ProtobufParser.Mode.SEGMENTED : ProtobufParser.Mode.STRUCTURED;
            segmented = false;
            return mode;
        }
    }

    // the reused, call-scoped diagnostic pushed to the reporter: its message is whatever the rejecting
    // component populated — the parser's caught exception, or the validator's structural-reject exception
    private static final class Diagnostic implements ProtobufDiagnostic
    {
        private String message;

        @Override
        public String message()
        {
            return message;
        }
    }
}
