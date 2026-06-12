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
    private int segmentWritten;
    private JsonEvent pendingSegmentEvent;

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
        DirectBuffer segment;
        switch (event)
        {
        case KEY_NAME:
            generator.writeKey(source.getString());
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
            segment = source.getSegment();
            if (segmentWritten == 0 && segment.capacity() < generator.remaining())
            {
                // fits: re-encode normalized (a re-encode is never longer than the raw token)
                generator.write(source.getString());
                status = scalarStatus();
            }
            else
            {
                // too large for the bound: splice the raw token bytes verbatim, fragmenting across chunks
                if (segmentWritten == 0)
                {
                    generator.writeRaw(segment, 0, 0);
                }
                status = writeChunk(segment, event);
            }
            break;
        case VALUE_NUMBER:
            generator.writeNumber(source.getString());
            status = scalarStatus();
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
        case START_SEGMENT:
            segment = source.getSegment();
            // emit the value's leading separator once, before its first content byte
            generator.writeRaw(segment, 0, 0);
            status = writeChunk(segment, event);
            break;
        case CONTINUE_SEGMENT:
        case END_SEGMENT:
            segment = source.getSegment();
            status = writeChunk(segment, event);
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
        Status status = pendingSegmentEvent != null
            ? writeChunk(source.getSegment(), pendingSegmentEvent)
            : Status.ADVANCED;
        return boundary(status);
    }

    @Override
    public void reset()
    {
        depth = 0;
        segmentWritten = 0;
        pendingSegmentEvent = null;
        generator.reset();
    }

    // Writes as much of the current segment slice as the bounded output allows, deferring the rest: when
    // the slice does not fit it stashes the remainder and reports SUSPENDED so the driver drains and a
    // later resume() continues from segmentWritten; once the slice is fully written the value boundary is
    // reached.
    private Status writeChunk(
        DirectBuffer segment,
        JsonEvent event)
    {
        int sliceLength = segment.capacity();
        int length = Math.min(sliceLength - segmentWritten, generator.remaining());
        int deferred = sliceLength - segmentWritten - length;
        generator.writeSegment(segment, segmentWritten, length, deferred);
        segmentWritten += length;
        Status status;
        if (deferred > 0)
        {
            pendingSegmentEvent = event;
            status = Status.SUSPENDED;
        }
        else
        {
            segmentWritten = 0;
            pendingSegmentEvent = null;
            status = event == JsonEvent.END_SEGMENT || event == JsonEvent.VALUE_STRING
                ? scalarStatus()
                : Status.ADVANCED;
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
