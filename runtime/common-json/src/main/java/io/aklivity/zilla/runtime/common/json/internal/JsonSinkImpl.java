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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Terminal {@link JsonSink} that materializes each fed event into the corresponding {@code writeXxx}
 * call on the wrapped {@link JsonGeneratorEx}. Reaches {@link Status#COMPLETED} when the current
 * top-level value closes at depth zero. A {@link JsonEvent#isVerbatim()} event copies the original
 * source bytes instead of re-serializing — the byte-preserving fidelity an upstream mediator (e.g. a
 * validator) emits when this sink opts in via {@link JsonController#verbatim()}.
 */
public final class JsonSinkImpl implements JsonSink
{
    private static final int HEADROOM = 16;

    private final JsonGeneratorEx generator;
    private final Delivery delivery;
    private final boolean verbatim;
    private int depth;

    public JsonSinkImpl(
        JsonGeneratorEx generator)
    {
        this(generator, Delivery.STRUCTURED, false);
    }

    public JsonSinkImpl(
        JsonGeneratorEx generator,
        Delivery delivery)
    {
        this(generator, delivery, false);
    }

    public JsonSinkImpl(
        JsonGeneratorEx generator,
        Delivery delivery,
        boolean verbatim)
    {
        this.generator = generator;
        this.delivery = delivery;
        this.verbatim = verbatim;
    }

    @Override
    public Status feed(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status = Status.ADVANCED;
        switch (event)
        {
        case KEY_NAME:
            generator.writeKey(source.getStringView());
            break;
        case START_OBJECT:
            generator.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            generator.writeEnd();
            depth--;
            if (depth == 0)
            {
                status = Status.COMPLETED;
            }
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case SEGMENT:
            status = writeValue(control, source, event);
            break;
        case VERBATIM:
            // a byte-preserving event from an upstream mediator: copy the original source bytes; the mediator
            // owns structure (and so document completion), the sink is only the byte conduit here
            status = writeVerbatim(source);
            break;
        case VALUE_TRUE:
            generator.write(true);
            status = scalarStatus();
            break;
        case VALUE_FALSE:
            generator.write(false);
            status = scalarStatus();
            break;
        case VALUE_NULL:
            generator.writeNull();
            status = scalarStatus();
            break;
        case START_DOCUMENT:
            if (delivery == Delivery.SEGMENTABLE)
            {
                control.segmentable();
            }
            else if (verbatim)
            {
                control.verbatim();
            }
            break;
        case END_DOCUMENT:
            break;
        default:
            break;
        }

        return boundary(status);
    }

    @Override
    public Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status;
        if (event != null && event.isVerbatim())
        {
            status = writeVerbatim(source);
        }
        else
        {
            // the pump supplies the event that suspended; continue only while its value still has an unwritten
            // remainder (a boundary drain after a completed value, or a structural event, leaves none)
            status = inFlight(source, event) ? writeValue(control, source, event) : Status.ADVANCED;
        }
        return boundary(status);
    }

    @Override
    public Status flush(
        JsonController control,
        JsonSource source)
    {
        Status status = Status.ADVANCED;
        if (verbatim)
        {
            // drain bytes the parser consumed during end-of-window lookahead (e.g. a separator after the last
            // value) that no event pulled, so they are not lost when this window is replaced; the run cursor
            // makes this a no-op when nothing trails the last pulled event
            status = writeVerbatim(source);
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
        generator.reset();
    }

    // Whether the suspended event still has value bytes/chars left to write, read from the source cursor —
    // the sink keeps no pending state of its own.
    private boolean inFlight(
        JsonSource source,
        JsonEvent event)
    {
        boolean result;
        switch (event)
        {
        case VALUE_STRING:
        case VALUE_NUMBER:
            result = source.getStringView().length() > 0;
            break;
        case SEGMENT:
            result = source.getSegment().capacity() > 0;
            break;
        default:
            result = false;
            break;
        }
        return result;
    }

    // Writes one scalar/segment value, fed fresh or resumed: a structured (non-SEGMENTABLE) string renders
    // canonically from its decoded char view; a verbatim string, number lexeme, or segment splices raw
    // bytes.
    private Status writeValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status;
        if (event == JsonEvent.VALUE_NUMBER || event == JsonEvent.VALUE_STRING)
        {
            // a structured scalar renders canonically from its char view; a verbatim value arrives as a
            // SEGMENT, so getSegment() is reached only for segmented events
            status = writeScalar(control, source, event);
        }
        else
        {
            status = writeChunk(control, source.getSegment(), source);
        }
        return status;
    }

    // Renders a structured scalar canonically from its decoded char view: the generator owns quoting and
    // escaping (a string) or emits the lexeme (a number), writing only what fits the bound and reporting
    // consumed source chars so the parser advances its char cursor and re-exposes the remainder on resume —
    // the char-domain analog of writeChunk.
    private Status writeScalar(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        final boolean deferred = source.deferredBytes();
        final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        final CharSequence view = source.getStringView();
        final int available = view.length();
        final int before = generator.consumed();
        if (event == JsonEvent.VALUE_NUMBER)
        {
            generator.writeNumber(view, completion);
        }
        else
        {
            generator.write(view, completion);
        }
        final int consumed = generator.consumed() - before;
        control.consumed(consumed);
        Status status;
        if (available - consumed > 0)
        {
            status = Status.SUSPENDED;
        }
        else
        {
            status = deferred ? Status.ADVANCED : scalarStatus();
        }
        return status;
    }

    // Splices the segment view, the generator owning the value's leading separator (emitted once on the
    // first fragment) so the sink keeps no state; pushes back the source bytes the generator took via
    // control.consumed(...) so the upstream re-exposes the remainder.
    private Status writeChunk(
        JsonController control,
        DirectBuffer segment,
        JsonSource source)
    {
        boolean deferred = source.deferredBytes();
        Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
        int available = segment.capacity();
        int before = generator.consumed();
        generator.writeSegment(segment, 0, available, completion);
        int consumed = generator.consumed() - before;
        int outputDeferred = available - consumed;
        control.consumed(consumed);
        Status status;
        if (outputDeferred > 0)
        {
            status = Status.SUSPENDED;
        }
        else if (deferred)
        {
            status = Status.ADVANCED;
        }
        else
        {
            status = scalarStatus();
        }
        return status;
    }

    // Copies as much of the pending verbatim run as fits the bounded output, advancing the source cursor by
    // exactly what it returned. The pull is pre-bounded to the free output space, so a 1:1 copy always fits and
    // the returned length is the drain signal: fewer bytes than asked for means the run reached the parse
    // frontier (drained, ADVANCED); a full-bound pull means the output bound capped the run, so suspend and
    // resume against a freshly drained buffer. Document completion is the mediator's responsibility (it owns
    // structure), so a verbatim copy never reports COMPLETED itself.
    private Status writeVerbatim(
        JsonSource source)
    {
        final int free = generator.remaining();
        final DirectBuffer run = source.getVerbatim(free);
        final int length = run.capacity();
        if (length > 0)
        {
            generator.writeVerbatim(run, 0, length);
        }
        return length < free ? Status.ADVANCED : Status.SUSPENDED;
    }

    // Suspends at an event boundary once the bounded output nears its limit, so the next event's write
    // starts against a freshly drained buffer.
    private Status boundary(
        Status status)
    {
        Status result = status;
        if (status == Status.ADVANCED && generator.length() > 0 && generator.remaining() < HEADROOM)
        {
            result = Status.SUSPENDED;
        }
        return result;
    }

    private Status scalarStatus()
    {
        return depth == 0 ? Status.COMPLETED : Status.ADVANCED;
    }
}
