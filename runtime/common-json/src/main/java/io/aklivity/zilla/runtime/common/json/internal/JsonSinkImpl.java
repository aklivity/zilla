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
    private boolean valueStarted;
    private boolean pendingSegment;

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
            // splice the kept leaf's raw token bytes verbatim, fragmenting across chunks; the value's
            // leading separator is emitted once, before its first content byte
            segment = source.getSegment();
            if (!valueStarted)
            {
                generator.writeRaw(segment, 0, 0);
                valueStarted = true;
            }
            status = writeChunk(segment, source);
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
        Status status = pendingSegment ? writeChunk(source.getSegment(), source) : Status.ADVANCED;
        return boundary(status);
    }

    @Override
    public void reset()
    {
        depth = 0;
        segmentWritten = 0;
        valueStarted = false;
        pendingSegment = false;
        generator.reset();
    }

    // Writes as much of the current segment slice as the bounded output allows, deferring the rest: when
    // the slice does not fit it stashes the remainder (pendingSegment) and reports SUSPENDED so the driver
    // drains and a later resume() continues from segmentWritten. Once the slice is fully written the value
    // continues (more fragments follow, source.deferredBytes()) or completes (the value boundary).
    private Status writeChunk(
        DirectBuffer segment,
        JsonSource source)
    {
        int sliceLength = segment.capacity();
        int length = Math.min(sliceLength - segmentWritten, generator.remaining());
        int outputDeferred = sliceLength - segmentWritten - length;
        generator.writeSegment(segment, segmentWritten, length, outputDeferred);
        segmentWritten += length;
        Status status;
        if (outputDeferred > 0)
        {
            pendingSegment = true;
            status = Status.SUSPENDED;
        }
        else
        {
            segmentWritten = 0;
            pendingSegment = false;
            if (source.deferredBytes())
            {
                status = Status.ADVANCED;
            }
            else
            {
                valueStarted = false;
                status = scalarStatus();
            }
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
