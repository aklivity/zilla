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

import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufEncoder;

/**
 * Converts the proto3 JSON mapping of a message, read by the {@code common-json} buffer-backed
 * parser, into Protobuf wire format. Reuse a single instance per worker thread.
 */
public final class JsonToProtobuf
{
    private final ProtobufEncoder encoder;
    private final JsonParserEx parser;

    JsonToProtobuf(
        ProtobufSchema schema)
    {
        this.encoder = new ProtobufEncoder(schema);
        this.parser = StreamingJson.createParser();
    }

    /**
     * Parses the JSON encoding of the message named {@code messageName} from
     * {@code json[offset, offset+length)} and writes its Protobuf wire encoding into {@code out}
     * starting at {@code outOffset}, returning the number of wire bytes written.
     */
    public int convert(
        String messageName,
        DirectBuffer json,
        int offset,
        int length,
        MutableDirectBuffer out,
        int outOffset)
    {
        parser.wrap(json, offset, length);
        return encoder.encode(messageName, parser, out, outOffset);
    }
}
