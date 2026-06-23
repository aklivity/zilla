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
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.SUSPENDED;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Terminal {@link AvroSink} that adapts the parsed {@link AvroEvent} stream to typed calls on the
 * wrapped {@link AvroGenerator}, reading each value from the {@link AvroSource}. Field names carry no
 * wire information for Avro, so {@code FIELD_NAME} is skipped; the generator advances positionally.
 * <p>
 * Output is bounded and honored strictly: the sink suspends <em>before</em> a value that would not fit
 * the generator's {@link AvroGenerator#remaining()} (with {@link #HEADROOM} for a small fixed-size
 * value), so nothing is ever written past the limit. A length-delimited value too large for one chunk
 * is streamed in pieces via {@link AvroGenerator#writeSegment}, suspending between pieces. On
 * {@code SUSPENDED} the pipeline re-delivers the same event after the caller drains and re-wraps, so a
 * scalar resumes whole in a fresh chunk and a segmented value resumes from where it paused.
 */
public final class AvroSinkImpl implements AvroSink
{
    private static final int HEADROOM = 16;

    private final AvroGenerator generator;
    private final Delivery delivery;
    private int depth;

    public AvroSinkImpl(
        AvroGenerator generator)
    {
        this(generator, Delivery.STRUCTURED);
    }

    public AvroSinkImpl(
        AvroGenerator generator,
        Delivery delivery)
    {
        this.generator = generator;
        this.delivery = delivery;
    }

    @Override
    public Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        Status status = ADVANCED;
        DirectBuffer segment;
        switch (event)
        {
        case START_MESSAGE:
            if (delivery == Delivery.SEGMENTABLE)
            {
                control.segmentable();
            }
            break;
        case START_RECORD:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeStartRecord();
                depth++;
            }
            break;
        case START_ARRAY:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeStartArray();
                depth++;
            }
            break;
        case START_MAP:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeStartMap();
                depth++;
            }
            break;
        case END_RECORD:
        case END_ARRAY:
        case END_MAP:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeEnd();
                status = close();
            }
            break;
        case MAP_KEY:
            status = atomic();
            if (status == ADVANCED)
            {
                segment = source.getSegment();
                generator.writeKey(segment, 0, segment.capacity());
            }
            break;
        case UNION_BRANCH:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeIndex(source.getInt());
            }
            break;
        case NULL:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeNull();
                status = scalar();
            }
            break;
        case BOOLEAN:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeBoolean(source.getBoolean());
                status = scalar();
            }
            break;
        case INT:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeInt(source.getInt());
                status = scalar();
            }
            break;
        case LONG:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeLong(source.getLong());
                status = scalar();
            }
            break;
        case FLOAT:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeFloat(source.getFloat());
                status = scalar();
            }
            break;
        case DOUBLE:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeDouble(source.getDouble());
                status = scalar();
            }
            break;
        case ENUM:
            status = atomic();
            if (status == ADVANCED)
            {
                generator.writeEnum(source.getInt());
                status = scalar();
            }
            break;
        case STRING:
        case BYTES:
        case FIXED:
            status = writeValue(control, source);
            break;
        case SEGMENT:
            status = atomic();
            if (status == ADVANCED)
            {
                segment = source.getSegment();
                generator.writeRaw(segment, 0, segment.capacity());
                if (source.deferredBytes() == 0)
                {
                    status = scalar();
                }
            }
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public Status resume(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        return transform(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
    }

    @Override
    public boolean identity()
    {
        return generator.identity();
    }

    // Streams a length-delimited value (string/bytes/fixed) into the bounded generator without sink state:
    // the segment is the value's unconsumed remainder (the parser advances it on consumed()), so a resume
    // re-reads it from the source and continues. The generator writes the length prefix once and copies
    // only what fits the bound; consumed() pushes the written payload back so the parser re-exposes the rest.
    private Status writeValue(
        AvroController control,
        AvroSource source)
    {
        Status status = atomic();
        if (status == ADVANCED)
        {
            DirectBuffer segment = source.getSegment();
            int available = segment.capacity();
            int deferred = source.deferredBytes();
            int written = generator.writeSegment(segment, 0, available, deferred);
            if (written > 0)
            {
                control.consumed(written);
            }
            if (written < available)
            {
                status = SUSPENDED;
            }
            else if (deferred == 0)
            {
                generator.flush();
                status = scalar();
            }
        }
        return status;
    }

    private Status atomic()
    {
        return generator.length() > 0 && generator.remaining() < HEADROOM ? SUSPENDED : ADVANCED;
    }

    private Status close()
    {
        depth--;
        return depth == 0 ? COMPLETED : ADVANCED;
    }

    private Status scalar()
    {
        return depth == 0 ? COMPLETED : ADVANCED;
    }
}
