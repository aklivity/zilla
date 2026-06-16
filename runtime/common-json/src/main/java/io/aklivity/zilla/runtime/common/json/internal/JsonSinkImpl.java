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
 * top-level value closes at depth zero.
 */
public final class JsonSinkImpl implements JsonSink
{
    private static final int HEADROOM = 16;

    private final JsonGeneratorEx generator;
    private final Delivery delivery;
    private int depth;
    private boolean valueStarted;
    // the value event currently mid-write across a bounded-output suspend, or null when nothing is in
    // flight; resume() re-runs that value's write, and the delivery mode resolves a decoded string from a
    // verbatim one. A boundary() suspend at an event boundary leaves this null, so resume just advances.
    private JsonEvent pending;

    public JsonSinkImpl(
        JsonGeneratorEx generator)
    {
        this(generator, Delivery.STRUCTURED);
    }

    public JsonSinkImpl(
        JsonGeneratorEx generator,
        Delivery delivery)
    {
        this.generator = generator;
        this.delivery = delivery;
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
            generator.writeKey(source.getKey());
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
        JsonSource source)
    {
        Status status = pending != null ? writeValue(control, source, pending) : Status.ADVANCED;
        return boundary(status);
    }

    @Override
    public void reset()
    {
        depth = 0;
        valueStarted = false;
        pending = null;
        generator.reset();
    }

    // Writes one scalar/segment value, fed fresh or resumed: a structured (non-SEGMENTABLE) string renders
    // canonically from its decoded char view; a verbatim string, number lexeme, or segment splices raw
    // bytes. Records the event as pending while the bounded output leaves it mid-write so resume re-runs it.
    private Status writeValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status;
        if (event == JsonEvent.VALUE_NUMBER ||
            event == JsonEvent.VALUE_STRING && delivery != Delivery.SEGMENTABLE)
        {
            status = writeScalar(control, source, event);
        }
        else
        {
            final DirectBuffer segment = source.getSegment();
            if (!valueStarted)
            {
                // emit the value's leading separator once, before its first content byte
                generator.writeRaw(segment, 0, 0);
                valueStarted = true;
            }
            status = writeChunk(control, segment, source);
        }
        pending = status == Status.SUSPENDED ? event : null;
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

    // Writes the segment view whole and pushes back the source bytes the generator took via
    // control.consumed(...) so the upstream re-exposes the remainder.
    private Status writeChunk(
        JsonController control,
        DirectBuffer segment,
        JsonSource source)
    {
        int available = segment.capacity();
        int before = generator.consumed();
        generator.writeSegment(segment, 0, available);
        int consumed = generator.consumed() - before;
        int outputDeferred = available - consumed;
        control.consumed(consumed);
        Status status;
        if (outputDeferred > 0)
        {
            status = Status.SUSPENDED;
        }
        else if (source.deferredBytes())
        {
            status = Status.ADVANCED;
        }
        else
        {
            valueStarted = false;
            status = scalarStatus();
        }
        return status;
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
