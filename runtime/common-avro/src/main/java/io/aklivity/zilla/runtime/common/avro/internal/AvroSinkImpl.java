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
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroGeneratorEx;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Terminal {@link AvroSink} that materializes each fed event into a write on the wrapped
 * {@link AvroGeneratorEx}. Reaches {@link Status#COMPLETE} when the current top-level datum closes at
 * depth zero. In {@link Delivery#SEGMENTABLE} mode it requests verbatim segment delivery on
 * {@link AvroEvent#START_DOCUMENT} and appends each segment slice raw.
 */
public final class AvroSinkImpl implements AvroSink
{
    private final AvroGeneratorEx generator;
    private final Delivery delivery;
    private int depth;

    public AvroSinkImpl(
        AvroGeneratorEx generator)
    {
        this(generator, Delivery.STRUCTURED);
    }

    public AvroSinkImpl(
        AvroGeneratorEx generator,
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
        Status status = PENDING;
        DirectBuffer segment;
        switch (event)
        {
        case START_DOCUMENT:
            if (delivery == Delivery.SEGMENTABLE)
            {
                control.segmentable();
            }
            break;
        case END_DOCUMENT:
            break;
        case RECORD_START:
        case ARRAY_START:
        case MAP_START:
            generator.encode(event, source);
            depth++;
            break;
        case RECORD_END:
        case ARRAY_END:
        case MAP_END:
            generator.encode(event, source);
            depth--;
            status = depth == 0 ? COMPLETE : PENDING;
            break;
        case FIELD_NAME:
        case MAP_KEY:
        case UNION_BRANCH:
            generator.encode(event, source);
            break;
        case START_SEGMENT:
        case CONTINUE_SEGMENT:
            segment = source.getSegment();
            generator.writeSegment(segment, 0, segment.capacity());
            break;
        case END_SEGMENT:
            segment = source.getSegment();
            generator.writeSegment(segment, 0, segment.capacity());
            status = depth == 0 ? COMPLETE : PENDING;
            break;
        default:
            generator.encode(event, source);
            status = depth == 0 ? COMPLETE : PENDING;
            break;
        }
        return status;
    }

    @Override
    public void reset()
    {
        depth = 0;
    }
}
