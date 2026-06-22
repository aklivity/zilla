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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A terminal sink for the schema-free pipeline: writes each generic event back out as wire by its
 * {@link ProtobufSource#fieldNumber()} and {@link ProtobufSource#wireType()}, splicing the raw value
 * slice verbatim. Composed with the schema-free parser it is a lossless structural copy; a transform
 * between them can keep/drop/redact fields by number with no schema.
 * <p>
 * A length-delimited value may arrive in pieces (chunked input, {@link ProtobufSource#deferredBytes()}
 * {@code > 0}) and may exceed the bounded output, so it streams through {@link ProtobufGenerator#writeSegment}
 * — its total-length prefix written once, its body across chunks — mirroring the typed sink.
 */
public final class ProtobufUntypedSinkImpl implements ProtobufSink
{
    private final ProtobufGenerator generator;

    private int depth;

    public ProtobufUntypedSinkImpl(
        ProtobufGenerator generator)
    {
        this.generator = generator;
    }

    @Override
    public ProtobufPipeline.Status transform(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        return dispatch(control, source, event);
    }

    @Override
    public ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        return dispatch(control, source, event);
    }

    @Override
    public void reset()
    {
        depth = 0;
    }

    private ProtobufPipeline.Status dispatch(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.ADVANCED;
        switch (event)
        {
        case START_MESSAGE:
            depth++;
            break;
        case END_MESSAGE:
            depth--;
            if (depth == 0)
            {
                status = ProtobufPipeline.Status.COMPLETED;
            }
            break;
        case VALUE:
            status = onValue(control, source);
            break;
        default:
            break;
        }
        return status;
    }

    private ProtobufPipeline.Status onValue(
        ProtobufController control,
        ProtobufSource source)
    {
        ProtobufPipeline.Status status;
        if (source.wireType() == ProtobufWireType.LEN)
        {
            // a length-delimited value streams through the consumption-driven generator; the sink pushes the
            // unconsumed remainder back via control.consumed() and the source re-exposes it on resume, so the
            // sink keeps no write cursor
            DirectBuffer segment = source.segment();
            int available = segment.capacity();
            int deferred = source.deferredBytes();
            int before = generator.consumed();
            generator.writeSegment(source.fieldNumber(), segment, 0, available, deferred);
            int written = generator.consumed() - before;
            control.consumed(written);
            if (written < available)
            {
                if (generator.length() > 0)
                {
                    generator.flush();
                    status = ProtobufPipeline.Status.SUSPENDED;
                }
                else
                {
                    throw new ProtobufException("value header exceeds output limit");
                }
            }
            else
            {
                status = ProtobufPipeline.Status.ADVANCED;
            }
        }
        else
        {
            DirectBuffer segment = source.segment();
            generator.writeValue(source.fieldNumber(), source.wireType(), segment, 0, segment.capacity());
            status = ProtobufPipeline.Status.ADVANCED;
        }
        return status;
    }
}
