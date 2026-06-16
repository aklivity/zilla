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
package io.aklivity.zilla.runtime.common.protobuf.json;

import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.json.internal.ProtobufJsonGeneratorImpl;
import io.aklivity.zilla.runtime.common.protobuf.json.internal.ProtobufJsonParserImpl;

/**
 * The protobuf ↔ JSON bridge, composing the {@code common-protobuf} wire layer with the
 * {@code common-json} transcoder by adapting the wire {@link ProtobufParser} and {@link ProtobufGenerator}
 * to a {@link JsonParserEx} and {@link JsonGeneratorEx}. It owns the proto3 JSON mapping (64-bit integers as
 * strings, {@code bytes} as base64, enums as names, {@code map} as object, {@code repeated} as array, proto3
 * json names), leaving each side's library single-format.
 * <p>
 * Both adapters drop into the existing {@link io.aklivity.zilla.runtime.common.protobuf.Protobuf} pipeline
 * machinery unchanged — as a pure {@link ProtobufParser} ↔ {@link ProtobufGenerator} pair, or via a
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufStream} into a
 * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufSink}:
 * <ul>
 * <li><b>JSON → protobuf</b> — {@link #parser(JsonParserEx, ProtobufSchema, String)}:
 * <pre>{@code
 * Protobuf.stream(ProtobufJson.parser(jsonParser, schema, "Person"))
 *     .into(ProtobufSink.of(wireGenerator, schema, "Person"));
 * }</pre></li>
 * <li><b>protobuf → JSON</b> — {@link #generator(JsonGeneratorEx, ProtobufSchema, String)}:
 * <pre>{@code
 * Protobuf.stream(Protobuf.parser(schema, "Person"))
 *     .into(ProtobufSink.of(ProtobufJson.generator(jsonGenerator, schema, "Person"), schema, "Person"));
 * }</pre></li>
 * </ul>
 */
public final class ProtobufJson
{
    /**
     * A {@link ProtobufParser} that reads JSON through {@code parser}, mapping each JSON value onto the field
     * of the message named {@code messageName} in {@code schema} (by proto3 json name then proto name) and
     * presenting it as the matching protobuf event so a wire {@link ProtobufGenerator} (or a whole pipeline)
     * encodes it. Streams windowed JSON input, returning {@code null} from {@code nextEvent} on a partial
     * window and continuing via {@code resume}.
     */
    public static ProtobufParser parser(
        JsonParserEx parser,
        ProtobufSchema schema,
        String messageName)
    {
        return new ProtobufJsonParserImpl(parser, schema, messageName);
    }

    /**
     * A {@link ProtobufGenerator} that renders the wire write calls as JSON through {@code generator},
     * resolving each field by number against the message named {@code messageName} in {@code schema} and
     * applying the proto3 JSON mapping. The root object opens on the first write; call {@link
     * ProtobufGenerator#flush()} after the pipeline reports
     * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status#COMPLETED} to finalize the
     * document, then read {@link ProtobufGenerator#length()}.
     */
    public static ProtobufGenerator generator(
        JsonGeneratorEx generator,
        ProtobufSchema schema,
        String messageName)
    {
        return new ProtobufJsonGeneratorImpl(generator, schema, messageName);
    }

    private ProtobufJson()
    {
    }
}
