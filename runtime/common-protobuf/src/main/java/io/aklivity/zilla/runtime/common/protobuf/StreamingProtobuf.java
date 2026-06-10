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

import io.aklivity.zilla.runtime.common.protobuf.internal.DescriptorSetCompiler;

/**
 * Entry point for streaming Protobuf over Agrona buffers. Compile a {@link ProtobufSchema} once per
 * {@code schemaId} and cache it, then obtain a reusable per-worker {@link ProtobufCanonicalizer}.
 * <p>
 * This is the format-neutral wire layer only; it has no JSON dependency. The protobuf ↔ JSON
 * mapping is composed in {@code model-protobuf}, which depends on both {@code common-protobuf} and
 * {@code common-json}.
 * <p>
 * Bounded-buffer contract: operations run on a single, fully-buffered message — the engine delivers
 * the reassembled payload — so processing is bounded by the message size (and, for nested messages,
 * by nesting depth). No unbounded document is buffered.
 */
public final class StreamingProtobuf
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
        return new DescriptorSetCompiler().compile(fileDescriptorSet, offset, length);
    }

    /**
     * A reusable per-worker canonicalizer that re-serializes a message to canonical wire form, the
     * reference operation for binary round-trip conformance.
     */
    public static ProtobufCanonicalizer canonicalizer(
        ProtobufSchema schema)
    {
        return new ProtobufCanonicalizer(schema);
    }

    private StreamingProtobuf()
    {
    }
}
