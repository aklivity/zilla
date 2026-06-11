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
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.PENDING;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEncoder;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Terminal {@link AvroSink} that adapts the decoded {@link AvroEvent} stream to typed calls on the
 * wrapped {@link AvroEncoder}, reading each value from the {@link AvroSource}. Field names carry no
 * wire information for Avro, so {@code FIELD_NAME} is skipped; the encoder advances positionally.
 * Reaches {@link Status#COMPLETE} when the current top-level message closes at depth zero. In
 * {@link Delivery#SEGMENTABLE} mode it requests verbatim segment delivery on {@link AvroEvent#START_MESSAGE}
 * and appends each segment slice raw.
 */
public final class AvroSinkImpl implements AvroSink
{
    private final AvroEncoder encoder;
    private final Delivery delivery;
    private int depth;

    public AvroSinkImpl(
        AvroEncoder encoder)
    {
        this(encoder, Delivery.STRUCTURED);
    }

    public AvroSinkImpl(
        AvroEncoder encoder,
        Delivery delivery)
    {
        this.encoder = encoder;
        this.delivery = delivery;
    }

    @Override
    public Status feed(
        AvroController control,
        AvroSource source,
        AvroEvent event)
    {
        Status status = PENDING;
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
            encoder.startRecord();
            depth++;
            break;
        case END_RECORD:
            encoder.endRecord();
            status = close();
            break;
        case START_ARRAY:
            encoder.startArray();
            depth++;
            break;
        case END_ARRAY:
            encoder.endArray();
            status = close();
            break;
        case START_MAP:
            encoder.startMap();
            depth++;
            break;
        case END_MAP:
            encoder.endMap();
            status = close();
            break;
        case MAP_KEY:
            segment = source.getSegment();
            encoder.mapKey(segment, 0, segment.capacity());
            break;
        case UNION_BRANCH:
            encoder.unionBranch(source.getInt());
            break;
        case NULL:
            encoder.encodeNull();
            status = scalar();
            break;
        case BOOLEAN:
            encoder.encodeBoolean(source.getBoolean());
            status = scalar();
            break;
        case INT:
            encoder.encodeInt(source.getInt());
            status = scalar();
            break;
        case LONG:
            encoder.encodeLong(source.getLong());
            status = scalar();
            break;
        case FLOAT:
            encoder.encodeFloat(source.getFloat());
            status = scalar();
            break;
        case DOUBLE:
            encoder.encodeDouble(source.getDouble());
            status = scalar();
            break;
        case STRING:
            segment = source.getSegment();
            encoder.encodeString(segment, 0, segment.capacity());
            status = scalar();
            break;
        case BYTES:
            segment = source.getSegment();
            encoder.encodeBytes(segment, 0, segment.capacity());
            status = scalar();
            break;
        case FIXED:
            segment = source.getSegment();
            encoder.encodeFixed(segment, 0, segment.capacity());
            status = scalar();
            break;
        case ENUM:
            encoder.encodeEnum(source.getInt());
            status = scalar();
            break;
        case START_SEGMENT:
        case CONTINUE_SEGMENT:
            segment = source.getSegment();
            encoder.writeSegment(segment, 0, segment.capacity());
            break;
        case END_SEGMENT:
            segment = source.getSegment();
            encoder.writeSegment(segment, 0, segment.capacity());
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
        return depth == 0 ? COMPLETE : PENDING;
    }

    private Status scalar()
    {
        return depth == 0 ? COMPLETE : PENDING;
    }
}
