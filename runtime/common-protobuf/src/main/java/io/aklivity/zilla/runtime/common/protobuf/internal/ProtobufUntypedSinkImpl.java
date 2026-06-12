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

import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;

/**
 * A terminal sink for the schema-free pipeline: writes each generic event back out as wire by its
 * {@link ProtobufSource#fieldNumber()} and {@link ProtobufSource#wireType()}, splicing the raw value
 * slice verbatim. Composed with the schema-free parser it is a lossless structural copy; a transform
 * between them can keep/drop/redact fields by number with no schema.
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
    public ProtobufPipeline.Status feed(
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
            generator.writeValue(source.fieldNumber(), source.wireType(),
                source.buffer(), source.offset(), source.length());
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
}
