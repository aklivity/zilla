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
 * Wraps a {@code tools/call} response body that is not itself a JSON object -- e.g. a top-level array or
 * scalar returned by an OpenAPI operation with no explicit {@code output} override -- into
 * {@code {"result":<value>}} before it reaches the tool's output schema validator/projector. MCP's
 * {@code outputSchema} (and the {@code structuredContent} it describes) must be a JSON object, so
 * {@code McpOpenapiCompositeGenerator} advertises such tools with a {@code {"type":"object","properties":
 * {"result":<original schema>}}} outputSchema; this transform makes the real response body match that
 * shape rather than the raw, unwrapped value the upstream API actually returned.
 * <p>
 * Positioned immediately before the output schema's validator/projector in the response pipeline, so both
 * see the wrapped shape consistently. Injects the envelope's opening {@code {"result":} bytes as
 * generator-tracked events (mirroring {@link McpHttpToolResult}'s own envelope-injection technique), then
 * forwards the real value's own event stream unchanged, then injects the closing {@code }} once the real
 * value's root closes.
 */
public final class McpHttpResultWrap implements JsonTransform
{
    private static final String KEY_RESULT = "result";

    private static final int STEP_WRAP_START = 0;
    private static final int STEP_RESULT_KEY = 1;
    private static final int STEP_REAL_CONTENT = 2;
    private static final int STEP_WRAP_END = 3;
    private static final int STEP_DONE = 4;

    private final Resumable text = new Resumable();
    private final Control mediator = new Control(false);
    private final Control inject = new Control(true);

    private boolean started;
    private int step = STEP_WRAP_START;
    private int depth;
    private JsonEvent pendingEvent;

    @Override
    public void reset()
    {
        started = false;
        step = STEP_WRAP_START;
        depth = 0;
        pendingEvent = null;
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
            // effects, then begin injecting the wrapper's opening structure
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
                step = STEP_WRAP_END;
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
            step = STEP_WRAP_END;
            status = continueInjection(sink);
        }
        return status;
    }

    // Whether the just-forwarded event closed the real value's own root: a container event that brought
    // depth back to zero, or (a bare scalar root) a completed, non-deferred scalar seen at depth zero.
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

    // Drives the wrapper's steps in order. A step already started (returned SUSPENDED) is continued via
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

    private JsonEvent armStep(
        int step)
    {
        JsonEvent event;
        switch (step)
        {
        case STEP_WRAP_START:
            text.with("");
            event = JsonEvent.START_OBJECT;
            break;
        case STEP_RESULT_KEY:
            text.with(KEY_RESULT);
            event = JsonEvent.KEY_NAME;
            break;
        case STEP_WRAP_END:
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
