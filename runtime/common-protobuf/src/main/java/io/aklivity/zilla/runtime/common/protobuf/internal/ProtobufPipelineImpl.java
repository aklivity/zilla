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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufDiagnostic;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnvelope;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParsingException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufValidationException;
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
    private final ProtobufEnvelope envelope;
    private final ProtobufSink head;
    private final ProtobufReporter reporter;
    private final Diagnostic diagnostic;
    // the terminal generator the pipeline re-targets per transform, or null for a non-generator terminal
    private final ProtobufGenerator generator;
    private final ProtobufPipelineResult result;
    private final boolean lenient;

    private final ExpandableArrayBufferEx pendingBuffer;
    private ExpandableArrayBufferEx tail;
    private ExpandableArrayBufferEx tailSwap;
    // the unconsumed input the pipeline holds for itself once a stage returned PENDING, since the caller
    // re-drives a suspended pipeline without re-presenting what it already handed over
    private int tailLength;
    // the final window was seen, so a re-drive that carries no flags still ends the message
    private boolean inputLast;
    private final UnsafeBufferEx pendingSegment;

    private boolean suspended;
    private boolean starved;
    // the event in flight across an output suspend or an asynchronous wait, handed back to the stage chain so
    // no sink stores it
    private ProtobufEvent resumeEvent;
    // the innermost stage that returned PENDING, re-entered with resumeEvent once the caller drives again
    private ProtobufSink pending;
    // the pending event's slice, copied out of the input window the caller releases while the stage waits,
    // and served by the source view until the event is finally accepted
    private DirectBufferEx retained;
    private int pendingOffset;

    public ProtobufPipelineImpl(
        ProtobufParser parser,
        List<ProtobufTransform> transforms,
        ProtobufSink sink,
        ProtobufReporter reporter,
        ProtobufGenerator generator,
        boolean lenient,
        ProtobufEnvelope envelope)
    {
        this.parser = parser;
        this.reporter = reporter;
        this.diagnostic = new Diagnostic();
        this.generator = generator;
        this.lenient = lenient;
        this.result = new ProtobufPipelineResult();
        // the per-edge handles the stages see: a read-only source view of the parser, and a control handle
        // that records a stage's segment request which the pump turns into the SEGMENTED mode on the next pull
        this.source = new Source();
        this.control = new Control();
        this.envelope = envelope;
        this.pendingBuffer = new ExpandableArrayBufferEx();
        this.tail = new ExpandableArrayBufferEx();
        this.tailSwap = new ExpandableArrayBufferEx();
        this.pendingSegment = new UnsafeBufferEx(new byte[0]);

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
        diagnostic.category = null;
        resumeEvent = null;
        pending = null;
        retained = null;
        tailLength = 0;
        inputLast = false;
    }

    @Override
    public boolean identity()
    {
        return parser.identity() && head.identity();
    }

    @Override
    public boolean deterministic()
    {
        return parser.deterministic() && head.deterministic();
    }

    @Override
    public void authorization(
        long authorization)
    {
        control.authorization = authorization;
    }

    @Override
    public void bindingId(
        long bindingId)
    {
        control.bindingId = bindingId;
    }

    @Override
    public void resumer(
        Runnable resumer)
    {
        control.resumer = resumer;
    }

    @Override
    public int remaining()
    {
        return parser.remaining();
    }

    @Override
    public Status transform(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        Status status = Status.ADVANCED;
        try
        {
            if (pending != null)
            {
                // asynchronous wait: re-present the pending event to the stage that returned PENDING, over the
                // window re-presented at the cursor, before pulling anything further
                parser.resume(buffer, offset, limit, last);
                ProtobufSink reentry = pending;
                pending = null;
                status = reentry.transform(control, source, resumeEvent);
                if (status != Status.PENDING && status != Status.SUSPENDED)
                {
                    retained = null;
                }
            }
            else if (suspended)
            {
                // output back-pressure: continue the suspended work on the same window without replaying it
                status = head.resume(control, source, resumeEvent);
                if (status != Status.SUSPENDED)
                {
                    retained = null;
                }
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
                    status = head.transform(control, source, event);
                    if (status == Status.SUSPENDED || status == Status.PENDING)
                    {
                        // the pump owns the resume cursor: remember the in-flight event for the next entry
                        resumeEvent = event;
                    }
                    if (status == Status.PENDING)
                    {
                        retain(event);
                    }
                    // a sink STARVED leaves the unconsumed tail uncommitted (consumed() advanced the cursor by
                    // only what the sink took), so remaining() already reports it for the next window — no rewind
                }
            }
            suspended = status == Status.SUSPENDED;
            starved = status == Status.STARVED;
        }
        catch (ProtobufValidationException ex)
        {
            // inert today — no stage throws this yet; wired for when semantic validation lands
            diagnostic.message = ex.getMessage();
            diagnostic.category = ProtobufDiagnostic.Category.VALIDATION;
            reporter.rejected(diagnostic);
            status = lenient ? Status.COMPLETED : Status.REJECTED;
            suspended = false;
            starved = false;
        }
        catch (ProtobufParsingException ex)
        {
            status = Status.REJECTED;
            diagnostic.message = ex.getMessage();
            diagnostic.category = ProtobufDiagnostic.Category.PARSING;
        }
        catch (ProtobufException ex)
        {
            status = Status.REJECTED;
            diagnostic.message = ex.getMessage();
            diagnostic.category = ProtobufDiagnostic.Category.TRANSFORM;
        }
        if (status == Status.REJECTED)
        {
            // terminal failure only — never STARVED/SUSPENDED back-pressure; the diagnostic is a reused,
            // call-scoped view, so the reporter must copy out anything it needs before returning
            reporter.rejected(diagnostic);
        }
        return status;
    }

    @Override
    public ProtobufPipelineResult transform(
        DirectBufferEx src,
        int offset,
        int limit,
        boolean last,
        MutableDirectBufferEx dst,
        int dstOffset,
        int dstLimit)
    {
        // re-target the terminal generator at the caller's output region, preserving structural context
        // across a SUSPENDED drain, then pump the same window the existing transform contract expects; the
        // protobuf generator bounds writes by a length from the offset, so pass dstLimit - dstOffset
        generator.wrap(dst, dstOffset, dstLimit - dstOffset);
        int srcLength = limit - offset;
        boolean held = tailLength > 0;
        boolean appended = held && !suspended && srcLength > 0;
        if (appended)
        {
            tail.putBytes(tailLength, src, offset, srcLength);
            tailLength += srcLength;
        }
        DirectBufferEx window = held ? tail : src;
        int windowOffset = held ? 0 : offset;
        int windowLimit = held ? tailLength : limit;
        inputLast |= last;
        Status status = transform(window, windowOffset, windowLimit, inputLast);
        boolean rejected = status == Status.REJECTED;
        if (status == Status.COMPLETED)
        {
            // finalize the document: the wire generator's records are already closed (a no-op at completion),
            // while the JSON generator writes its closing structure here so the produced bytes are well-formed
            generator.flush();
        }
        if (status == Status.PENDING)
        {
            generator.pause();
        }
        int produced = rejected ? 0 : generator.length();
        int consumed;
        if (status == Status.PENDING || held && status != Status.SUSPENDED)
        {
            // the pipeline owns the window from here: the unconsumed tail is kept internally and everything
            // handed over counts as consumed, so the caller never re-presents it
            retainTail(window, windowLimit - parser.remaining(), windowLimit, status);
            consumed = srcLength;
        }
        else if (held)
        {
            consumed = appended ? srcLength : 0;
        }
        else
        {
            // SUSPENDED holds the input steady (drain and re-present the same window); otherwise the window
            // advanced by all but the unconsumed tail the caller re-presents at the front of the next window
            consumed = rejected || status == Status.SUSPENDED ? 0 : srcLength - parser.remaining();
        }
        return result.set(status, consumed, produced);
    }

    private void retainTail(
        DirectBufferEx window,
        int from,
        int to,
        Status status)
    {
        int length = status == Status.REJECTED || status == Status.COMPLETED ? 0 : to - from;
        if (window == tail)
        {
            if (length > 0 && from > 0)
            {
                tailSwap.putBytes(0, tail, from, length);
                ExpandableArrayBufferEx swap = tail;
                tail = tailSwap;
                tailSwap = swap;
            }
        }
        else if (length > 0)
        {
            tail.putBytes(0, window, from, length);
        }
        tailLength = length;
    }

    private void retain(
        ProtobufEvent event)
    {
        if (event == ProtobufEvent.VALUE || event == ProtobufEvent.SEGMENT || event == ProtobufEvent.START_MESSAGE)
        {
            DirectBufferEx segment = parser.segment();
            int length = segment.capacity();
            pendingBuffer.putBytes(0, segment, 0, length);
            pendingOffset = 0;
            pendingSegment.wrap(pendingBuffer, pendingOffset, length);
            retained = pendingSegment;
            if (event != ProtobufEvent.START_MESSAGE)
            {
                // commit the value past the read cursor so the caller carries only what follows it
                parser.consumed(length);
            }
        }
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
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event)
        {
            Status status = transform.transform(control, source, event, downstream);
            if (status == Status.PENDING && pending == null)
            {
                pending = this;
            }
            return status;
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

        @Override
        public boolean identity()
        {
            return transform.identity() && downstream.identity();
        }

        @Override
        public boolean deterministic()
        {
            return transform.deterministic() && downstream.deterministic();
        }
    }

    // the read-only view handed to stages: the parser's accessors without its cursor, so a stage reads the
    // current value but cannot advance the pump
    private final class Source implements ProtobufSource
    {
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
        public DirectBufferEx segment()
        {
            return retained != null ? retained : parser.segment();
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
        private long authorization;
        private long bindingId;
        private Runnable resumer = () ->
        {
        };

        @Override
        public long authorization()
        {
            return authorization;
        }

        @Override
        public long bindingId()
        {
            return bindingId;
        }

        @Override
        public ProtobufEnvelope envelope()
        {
            return envelope;
        }

        @Override
        public Runnable resumer()
        {
            return resumer;
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
            if (retained != null)
            {
                pendingOffset += sourceBytes;
                pendingSegment.wrap(pendingBuffer, pendingOffset, pendingSegment.capacity() - sourceBytes);
            }
            else
            {
                parser.consumed(sourceBytes);
            }
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
        private Category category;

        @Override
        public String message()
        {
            return message;
        }

        @Override
        public Category category()
        {
            return category;
        }
    }
}
