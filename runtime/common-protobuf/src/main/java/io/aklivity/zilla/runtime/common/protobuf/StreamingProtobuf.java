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

/**
 * Entry point for streaming Protobuf over Agrona buffers. Compile a {@link ProtobufSchema} once per
 * {@code schemaId} and cache it, then obtain reusable per-worker converters that decode the wire to
 * its proto3 JSON mapping and encode JSON back to the wire, composing with {@code common-json}.
 * <p>
 * Bounded-buffer contract: a converter operates on a single, fully-buffered message — the engine
 * delivers the reassembled payload — so the decode is a single bounded pass over the message bytes
 * and the encode stages nested messages in per-depth scratch bounded by message nesting. Neither
 * direction buffers an unbounded document.
 */
public final class StreamingProtobuf
{
    public static ProtobufSchema.Builder schema()
    {
        return ProtobufSchema.builder();
    }

    public static ProtobufToJson protobufToJson(
        ProtobufSchema schema)
    {
        return new ProtobufToJson(schema);
    }

    public static JsonToProtobuf jsonToProtobuf(
        ProtobufSchema schema)
    {
        return new JsonToProtobuf(schema);
    }

    private StreamingProtobuf()
    {
    }
}
