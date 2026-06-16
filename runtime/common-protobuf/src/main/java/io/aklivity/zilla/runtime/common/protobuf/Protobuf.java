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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufGeneratorImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufParserImpl;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufSchemaCompiler;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufStreamImpl;

/**
 * Entry point for streaming Protobuf over Agrona buffers. Compile a {@link ProtobufSchema} once per
 * {@code schemaId} and cache it, then obtain a reusable per-worker {@link ProtobufParser} or
 * {@link ProtobufGenerator}.
 * <p>
 * This is the format-neutral wire layer only; it has no JSON dependency. The protobuf ↔ JSON
 * mapping is owned by the {@code model-protobuf} converter, not this library.
 * <p>
 * Bounded-buffer contract: operations run on a single, fully-buffered message — the engine delivers
 * the reassembled payload — so processing is bounded by the message size (and, for nested messages,
 * by nesting depth). No unbounded document is buffered.
 */
public final class Protobuf
{
    public static ProtobufSchema.Builder schema()
    {
        return ProtobufSchema.builder();
    }

    /**
     * Compiles a serialized {@code google.protobuf.FileDescriptorSet} (e.g. {@code protoc
     * --descriptor_set_out}) into a {@link ProtobufSchema}, decoded with this library's own wire
     * reader so there is no {@code protobuf-java} dependency.
     */
    public static ProtobufSchema schema(
        DirectBuffer fileDescriptorSet,
        int offset,
        int length)
    {
        return new ProtobufSchemaCompiler().compile(fileDescriptorSet, offset, length);
    }

    /**
     * A schema-bound parser that decodes the message named {@code messageName} against {@code schema}
     * into a typed event stream. Pass it to {@link #stream(ProtobufParser)} to begin a pipeline, append
     * stages with {@link ProtobufStream#transform} (e.g. {@link ProtobufSchema#validator(String)}),
     * and terminate with {@link ProtobufStream#into}.
     */
    public static ProtobufParser parser(
        ProtobufSchema schema,
        String messageName)
    {
        return new ProtobufParserImpl(schema, messageName);
    }

    /**
     * A schema-free parser that tokenizes the wire into generic events — a {@link ProtobufEvent#FIELD}
     * per wire field (carrying {@link ProtobufSource#fieldNumber()} and {@link ProtobufSource#wireType()})
     * and a {@link ProtobufEvent#VALUE} carrying the raw value slice. Length-delimited values are opaque
     * bytes (no message-vs-string interpretation) and there is no recursion; suitable for generic
     * structural transforms (keep/drop/redact by field number) and lossless copy.
     */
    public static ProtobufParser parser()
    {
        return new ProtobufParserImpl(null, null);
    }

    /**
     * Begins a push pipeline pumped by {@code parser}: append stages with {@link ProtobufStream#transform}
     * and terminate with {@link ProtobufStream#into}. The {@code parser} (from {@link #parser(ProtobufSchema,
     * String)} or {@link #parser()}) supplies the events; stages see a non-advancing {@link ProtobufSource}
     * view of each.
     */
    public static ProtobufStream stream(
        ProtobufParser parser)
    {
        return new ProtobufStreamImpl(parser);
    }

    /**
     * A reusable per-worker buffer-backed wire generator, the output edge for a wire-writing
     * {@link ProtobufSink#of(ProtobufGenerator, ProtobufSchema, String)}.
     */
    public static ProtobufGenerator generator()
    {
        return new ProtobufGeneratorImpl();
    }

    private Protobuf()
    {
    }
}
