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

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.RESUMABLE;
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
 * Reaches {@link Status#COMPLETE} when the current top-level message closes at depth zero. In
 * {@link Delivery#SEGMENTABLE} mode it requests verbatim segment delivery on {@link AvroEvent#START_MESSAGE}
 * and appends each segment slice raw.
 * <p>
 * Output is bounded: after writing each value it returns {@link Status#SUSPENDED} once the generator's
 * {@link AvroGenerator#remaining()} drops below {@link #HEADROOM}. Avro wire is unframed (a datum is a
 * flat byte concatenation), so the bytes written so far are always a valid prefix; the caller drains
 * them, re-wraps the generator over a fresh buffer, and resumes — no level reopen or merge needed. A
 * single value larger than the output limit cannot be split and is written whole.
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
    public Status feed(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        Status status = RESUMABLE;
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
            generator.writeStartRecord();
            depth++;
            status = more();
            break;
        case START_ARRAY:
            generator.writeStartArray();
            depth++;
            status = more();
            break;
        case START_MAP:
            generator.writeStartMap();
            depth++;
            status = more();
            break;
        case END_RECORD:
        case END_ARRAY:
        case END_MAP:
            generator.writeEnd();
            status = close();
            break;
        case MAP_KEY:
            segment = source.getSegment();
            generator.writeKey(segment, 0, segment.capacity());
            status = more();
            break;
        case UNION_BRANCH:
            generator.writeIndex(source.getInt());
            status = more();
            break;
        case NULL:
            generator.writeNull();
            status = scalar();
            break;
        case BOOLEAN:
            generator.writeBoolean(source.getBoolean());
            status = scalar();
            break;
        case INT:
            generator.writeInt(source.getInt());
            status = scalar();
            break;
        case LONG:
            generator.writeLong(source.getLong());
            status = scalar();
            break;
        case FLOAT:
            generator.writeFloat(source.getFloat());
            status = scalar();
            break;
        case DOUBLE:
            generator.writeDouble(source.getDouble());
            status = scalar();
            break;
        case STRING:
            segment = source.getSegment();
            generator.writeString(segment, 0, segment.capacity());
            status = scalar();
            break;
        case BYTES:
            segment = source.getSegment();
            generator.writeBytes(segment, 0, segment.capacity());
            status = scalar();
            break;
        case FIXED:
            segment = source.getSegment();
            generator.writeFixed(segment, 0, segment.capacity());
            status = scalar();
            break;
        case ENUM:
            generator.writeEnum(source.getInt());
            status = scalar();
            break;
        case START_SEGMENT:
        case CONTINUE_SEGMENT:
            segment = source.getSegment();
            generator.writeRaw(segment, 0, segment.capacity());
            status = more();
            break;
        case END_SEGMENT:
            segment = source.getSegment();
            generator.writeRaw(segment, 0, segment.capacity());
            status = scalar();
            break;
        default:
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
    }

    private Status close()
    {
        depth--;
        return depth == 0 ? COMPLETE : more();
    }

    private Status scalar()
    {
        return depth == 0 ? COMPLETE : more();
    }

    private Status more()
    {
        return generator.length() > 0 && generator.remaining() < HEADROOM ? SUSPENDED : RESUMABLE;
    }
}
