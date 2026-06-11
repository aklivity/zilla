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
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

/**
 * A terminal sink for the schema-free pipeline: writes each generic event back out as wire by its
 * {@link ProtobufSource#fieldNumber()} and {@link ProtobufSource#wireType()}, splicing the raw value
 * slice verbatim. Composed with the schema-free parser it is a lossless structural copy; a transform
 * between them can keep/drop/redact fields by number with no schema.
 */
public final class ProtobufRawWireSinkImpl implements ProtobufSink
{
    private final ProtobufWriter writer;

    private int depth;

    public ProtobufRawWireSinkImpl(
        ProtobufGenerator generator)
    {
        this.writer = ((ProtobufGeneratorImpl) generator).writer();
    }

    @Override
    public ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        ProtobufPipeline.Status status = ProtobufPipeline.Status.RESUMABLE;
        switch (event)
        {
        case START_MESSAGE:
            depth++;
            break;
        case END_MESSAGE:
            depth--;
            if (depth == 0)
            {
                status = ProtobufPipeline.Status.COMPLETE;
            }
            break;
        case VALUE:
            write(source);
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

    private void write(
        ProtobufSource source)
    {
        int number = source.fieldNumber();
        ProtobufWireType wireType = source.wireType();
        switch (wireType)
        {
        case LEN:
            writer.writeTag(number, ProtobufWireType.LEN);
            writer.writeBytes(source.buffer(), source.offset(), source.length());
            break;
        case SGROUP:
            writer.writeTag(number, ProtobufWireType.SGROUP);
            writer.writeRaw(source.buffer(), source.offset(), source.length());
            writer.writeTag(number, ProtobufWireType.EGROUP);
            break;
        default:
            writer.writeTag(number, wireType);
            writer.writeRaw(source.buffer(), source.offset(), source.length());
            break;
        }
    }
}
