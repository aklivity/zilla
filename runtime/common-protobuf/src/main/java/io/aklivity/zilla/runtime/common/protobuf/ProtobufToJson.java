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
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufDecoder;

/**
 * Converts a fully-buffered Protobuf message to its proto3 JSON mapping, driving the
 * {@code common-json} buffer-backed generator. Reuse a single instance per worker thread.
 */
public final class ProtobufToJson
{
    private final ProtobufDecoder decoder;
    private final JsonGeneratorEx generator;

    ProtobufToJson(
        ProtobufSchema schema)
    {
        this.decoder = new ProtobufDecoder(schema);
        this.generator = StreamingJson.createGenerator();
    }

    /**
     * Decodes the Protobuf message named {@code messageName} from {@code in[offset, offset+length)}
     * and writes its JSON encoding into {@code out} starting at {@code outOffset}, returning the
     * number of JSON bytes written.
     */
    public int convert(
        String messageName,
        DirectBuffer in,
        int offset,
        int length,
        MutableDirectBuffer out,
        int outOffset)
    {
        generator.wrap(out, outOffset);
        decoder.decode(messageName, in, offset, length, generator);
        generator.flush();
        return generator.length();
    }
}
