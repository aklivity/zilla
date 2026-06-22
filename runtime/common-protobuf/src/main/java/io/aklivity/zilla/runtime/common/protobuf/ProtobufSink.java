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

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufTypedSinkImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufUntypedSinkImpl;

/**
 * The consume end of a {@link ProtobufStream} pipeline. Each {@link #transform(ProtobufController, ProtobufSource,
 * ProtobufEvent)} delivers one event (with {@code source} positioned to read its scalar, or its bytes when
 * the event is {@link ProtobufEvent#segmented()}) and returns whether the message has reached a terminal
 * {@link ProtobufPipeline.Status}. The downstream of a {@link ProtobufTransform} is also a {@code
 * ProtobufSink}. Third parties may implement this contract to consume the validated event stream.
 */
public interface ProtobufSink
{
    ProtobufPipeline.Status transform(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event);

    /**
     * Continues the work left unfinished when a prior {@link #transform} returned
     * {@link ProtobufPipeline.Status#SUSPENDED} — the pipeline calls this on resume instead of replaying
     * the event through the rest of the pipeline. {@code event} is the event that suspended (supplied by the
     * pump, so the sink keeps no resume cursor of its own), with {@code source} still positioned to read it.
     * The default has nothing pending and reports {@link ProtobufPipeline.Status#ADVANCED}; a bounded sink
     * overrides it to resume writing the in-flight field.
     */
    default ProtobufPipeline.Status resume(
        ProtobufController control,
        ProtobufSource source,
        ProtobufEvent event)
    {
        return ProtobufPipeline.Status.ADVANCED;
    }

    default void reset()
    {
    }

    /**
     * A terminal sink that writes the event stream out as Protobuf wire through {@code generator},
     * encoded against the message named {@code messageName} in {@code schema}, mapping each event's
     * field by name into the target message. When {@code schema} is the read schema this re-encodes;
     * when it differs this transforms (fields absent in the target are dropped). The generator must
     * already be wrapped over its output buffer; read {@link ProtobufGenerator#length()} after the
     * pipeline reports {@link ProtobufPipeline.Status#COMPLETED}.
     */
    static ProtobufSink of(
        ProtobufGenerator generator,
        ProtobufSchema schema,
        String messageName)
    {
        return new ProtobufTypedSinkImpl(generator, schema, messageName);
    }

    /**
     * A schema-free terminal sink that writes the generic event stream back out as wire through
     * {@code generator}, by field number and wire type, splicing each raw value verbatim. Composed
     * with {@code Protobuf.parser()} it is a lossless structural copy; place a transform
     * before it to keep/drop/redact fields by number with no schema.
     */
    static ProtobufSink of(
        ProtobufGenerator generator)
    {
        return new ProtobufUntypedSinkImpl(generator);
    }
}
