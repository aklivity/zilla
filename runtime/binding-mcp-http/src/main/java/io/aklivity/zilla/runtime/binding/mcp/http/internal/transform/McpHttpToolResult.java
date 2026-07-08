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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import java.math.BigDecimal;
import java.util.function.Supplier;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Wraps a {@code tools/call} success response's {@code structuredContent} value into the full
 * {@code {"structuredContent":<value>,"content":[{"type":"text","text":<summary>}],"isError":false}}
 * envelope, entirely as generator-tracked events: the envelope's outer object, every static structural
 * fragment, and the ({@code ${result.*}}-interpolated, potentially large) summary text all flow through
 * the same bounded generator the real {@code structuredContent} value does. A destination that fills
 * mid-summary suspends and resumes exactly like any other streamed value — no separate, unbounded
 * raw-byte write is needed for the envelope.
 * <p>
 * Positioned as the last stage before the terminal sink (after any validator/projector/capture stage),
 * so it sees the real content's fully-resolved event stream. It tracks the real value's own structural
 * depth to know when it closes, then injects the trailer as more events into the same sink, deferring
 * this pipeline's own {@link Status#COMPLETED} until the injected envelope itself closes.
 * <p>
 * A synthetic write that does not fit the destination resolves with {@link Status#SUSPENDED} exactly as
 * a real value does. Re-attempting a suspended step by calling {@link JsonSink#transform} again would
 * re-run its structural write (e.g. re-open an already-open object) and corrupt the generator's own
 * nesting — {@link JsonSink#resume} is the correct continuation, exactly as the pipeline itself uses for
 * a real value, so each trailer step is tracked and resumed the same way.
 */
public final class McpHttpToolResult implements JsonTransform
{
    private static final String KEY_STRUCTURED_CONTENT = "structuredContent";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TYPE = "type";
    private static final String VALUE_TYPE_TEXT = "text";
    private static final String KEY_TEXT = "text";
    private static final String KEY_IS_ERROR = "isError";

    private static final int STEP_ENVELOPE_START = 0;
    private static final int STEP_STRUCTURED_CONTENT_KEY = 1;
    private static final int STEP_REAL_CONTENT = 2;
    private static final int STEP_CONTENT_KEY = 3;
    private static final int STEP_CONTENT_ARRAY_START = 4;
    private static final int STEP_ITEM_START = 5;
    private static final int STEP_TYPE_KEY = 6;
    private static final int STEP_TYPE_VALUE = 7;
    private static final int STEP_TEXT_KEY = 8;
    private static final int STEP_TEXT_VALUE = 9;
    private static final int STEP_ITEM_END = 10;
    private static final int STEP_CONTENT_ARRAY_END = 11;
    private static final int STEP_IS_ERROR_KEY = 12;
    private static final int STEP_IS_ERROR_VALUE = 13;
    private static final int STEP_ENVELOPE_END = 14;
    private static final int STEP_DONE = 15;

    private final Supplier<String> summary;
    private final Resumable text = new Resumable();
    private final Control mediator = new Control(false);
    private final Control inject = new Control(true);

    private boolean started;
    private int step = STEP_ENVELOPE_START;
    private int depth;
    private JsonEvent pendingEvent;
    private String resolvedSummary;

    public McpHttpToolResult(
        Supplier<String> summary)
    {
        this.summary = summary;
    }

    @Override
    public void reset()
    {
        started = false;
        step = STEP_ENVELOPE_START;
        depth = 0;
        pendingEvent = null;
        resolvedSummary = null;
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediator.delegate = control;
        Status status;
        if (!started)
        {
            started = true;
            // the parser's own START_DOCUMENT: forward it to preserve its segmentable()/verbatim() side
            // effects, then begin injecting the envelope's opening structure
            status = sink.transform(mediator, source, event);
            if (status != Status.REJECTED)
            {
                status = continueInjection(sink);
            }
        }
        else if (step == STEP_REAL_CONTENT)
        {
            status = onRealEvent(source, event, sink);
        }
        else if (step == STEP_DONE)
        {
            status = sink.transform(mediator, source, event);
        }
        else
        {
            // defensive: no real event should arrive while a trailer step is still pending
            status = continueInjection(sink);
        }
        return status;
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        mediator.delegate = control;
        Status status;
        if (step == STEP_REAL_CONTENT)
        {
            status = sink.resume(control, source, event);
            if (status != Status.SUSPENDED && status != Status.REJECTED && isRootClose(source, event))
            {
                step = STEP_CONTENT_KEY;
                status = continueInjection(sink);
            }
        }
        else if (step == STEP_DONE)
        {
            status = sink.resume(control, source, event);
        }
        else
        {
            status = continueInjection(sink);
        }
        return status;
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source,
        JsonSink sink)
    {
        mediator.delegate = control;
        return sink.flush(mediator, source);
    }

    private Status onRealEvent(
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
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
        Status status = sink.transform(mediator, source, event);
        if (status != Status.REJECTED && status != Status.SUSPENDED && isRootClose(source, event))
        {
            step = STEP_CONTENT_KEY;
            status = continueInjection(sink);
        }
        return status;
    }

    // Whether the just-forwarded event closed the real structuredContent value's own root: a container
    // event that brought depth back to zero, or (a bare scalar root) a completed, non-deferred scalar
    // seen while still at depth zero.
    private boolean isRootClose(
        JsonSource source,
        JsonEvent event)
    {
        boolean result;
        switch (event)
        {
        case END_OBJECT:
        case END_ARRAY:
            result = depth == 0;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
        case SEGMENT:
            result = depth == 0 && !source.deferredBytes();
            break;
        default:
            result = false;
            break;
        }
        return result;
    }

    // Drives the trailer's steps in order. A step already started (returned SUSPENDED) is continued via
    // sink.resume(...) — never re-armed and re-transformed, which would re-run its structural write.
    private Status continueInjection(
        JsonSink sink)
    {
        Status status = Status.ADVANCED;
        if (pendingEvent != null)
        {
            status = sink.resume(inject, text, pendingEvent);
            if (status != Status.SUSPENDED)
            {
                pendingEvent = null;
                if (status != Status.REJECTED)
                {
                    step++;
                }
            }
        }
        while (status == Status.ADVANCED && step != STEP_REAL_CONTENT && step != STEP_DONE)
        {
            final JsonEvent event = armStep(step);
            status = sink.transform(inject, text, event);
            if (status == Status.SUSPENDED)
            {
                pendingEvent = event;
            }
            else if (status != Status.REJECTED)
            {
                step++;
            }
        }
        if (step == STEP_DONE)
        {
            status = Status.COMPLETED;
        }
        return status;
    }

    // Arms `text` with this step's source data (called exactly once per step, on its first attempt) and
    // returns the event to feed it as.
    private JsonEvent armStep(
        int step)
    {
        JsonEvent event;
        switch (step)
        {
        case STEP_ENVELOPE_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_STRUCTURED_CONTENT_KEY:
            text.with(KEY_STRUCTURED_CONTENT);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_CONTENT_KEY:
            text.with(KEY_CONTENT);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_CONTENT_ARRAY_START:
            text.with("");
            event = JsonEvent.START_ARRAY;
            break;
        case STEP_ITEM_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_TYPE_KEY:
            text.with(KEY_TYPE);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_TYPE_VALUE:
            text.with(VALUE_TYPE_TEXT);
            event = JsonEvent.VALUE_STRING;
            break;
        case STEP_TEXT_KEY:
            text.with(KEY_TEXT);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_TEXT_VALUE:
            if (resolvedSummary == null)
            {
                final String resolved = summary.get();
                resolvedSummary = resolved != null ? resolved : "";
            }
            text.with(resolvedSummary);
            event = JsonEvent.VALUE_STRING;
            break;
        case STEP_ITEM_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        case STEP_CONTENT_ARRAY_END:
            text.with("");
            event = JsonEvent.END_ARRAY;
            break;
        case STEP_IS_ERROR_KEY:
            text.with(KEY_IS_ERROR);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_IS_ERROR_VALUE:
            text.with("");
            event = JsonEvent.VALUE_FALSE;
            break;
        case STEP_ENVELOPE_END:
            text.with("");
            event = JsonEvent.END_OBJECT;
            break;
        default:
            throw new IllegalStateException("unexpected step: " + step);
        }
        return event;
    }

    // A resumable synthetic JsonSource: unlike a plain fixed-text source, getStringView() shrinks by
    // however much consumed() has reported, so a step suspended mid-write resumes from exactly where it
    // left off instead of re-presenting the whole value from the start.
    private static final class Resumable implements JsonSource
    {
        private CharSequence value;
        private int offset;

        private Resumable with(
            CharSequence value)
        {
            this.value = value;
            this.offset = 0;
            return this;
        }

        private void consumed(
            int count)
        {
            offset += count;
        }

        @Override
        public String getString()
        {
            return value == null ? null : getStringView().toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return value.subSequence(offset, value.length());
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonLocation getLocation()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }
    }

    private final class Control implements JsonController
    {
        private final boolean synthetic;

        private JsonController delegate;

        private Control(
            boolean synthetic)
        {
            this.synthetic = synthetic;
        }

        // Forwarded to the real delegate for a pass-through event (there is no reason to decline the
        // downstream sink's byte-preserving preference just because this mediator sits in the chain — a
        // synthetic event has no real source bytes to request in the first place, so it no-ops instead).
        @Override
        public void segmentable()
        {
            if (!synthetic)
            {
                delegate.segmentable();
            }
        }

        @Override
        public void verbatim()
        {
            if (!synthetic)
            {
                delegate.verbatim();
            }
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            if (synthetic)
            {
                text.consumed(sourceBytes);
            }
            else
            {
                delegate.consumed(sourceBytes);
            }
        }
    }
}
