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
package io.aklivity.zilla.runtime.common.protobuf;

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufDiscardSink;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufWireSink;

/**
 * The consume end of a {@link ProtobufStream} pipeline. Each {@link #feed(ProtobufController, ProtobufSource,
 * ProtobufEvent)} delivers one event (with {@code source} positioned to read its scalar, or its bytes when
 * the event is {@link ProtobufEvent#segmented()}) and returns whether the message has reached a terminal
 * {@link ProtobufPipeline.Status}. The downstream of a {@link ProtobufTransform} is also a {@code
 * ProtobufSink}. Third parties may implement this contract to consume the validated event stream.
 */
public interface ProtobufSink
{
    ProtobufPipeline.Status feed(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event);

    default void reset()
    {
    }

    /**
     * A terminal sink that consumes (and discards) the event stream, reaching
     * {@link ProtobufPipeline.Status#COMPLETE} at the end of the message. Useful as the terminal of a
     * pure validation pipeline, where the verdict is the pipeline {@link ProtobufPipeline.Status}.
     */
    static ProtobufSink discard()
    {
        return new ProtobufDiscardSink();
    }

    /**
     * A terminal sink that writes the event stream out as Protobuf wire through {@code generator},
     * encoded against the message named {@code messageName} in {@code schema}, mapping each event's
     * field by name into the target message. When {@code schema} is the read schema this re-encodes;
     * when it differs this transforms (fields absent in the target are dropped). The generator must
     * already be wrapped over its output buffer; read {@link ProtobufGenerator#length()} after the
     * pipeline reports {@link ProtobufPipeline.Status#COMPLETE}.
     */
    static ProtobufSink of(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        return new ProtobufWireSink(generator, schema, messageName);
    }
}
