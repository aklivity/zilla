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
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufStream;
import io.aklivity.zilla.runtime.common.protobuf.json.internal.JsonProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.json.internal.ProtobufJsonSink;

/**
 * The protobuf ↔ JSON bridge, composing the {@code common-protobuf} wire layer with the
 * {@code common-json} transcoder. It owns the proto3 JSON mapping (64-bit integers as strings, {@code bytes}
 * as base64, enums as names, {@code map} as object, {@code repeated} as array, well-known field/json names),
 * leaving each side's library single-format.
 * <p>
 * Two edges plug into the existing {@link Protobuf} pipeline machinery:
 * <ul>
 * <li><b>protobuf → JSON</b> — {@link #sink(JsonGeneratorEx)} returns a {@link ProtobufSink} that renders the
 * decoded protobuf event stream as JSON through a {@link JsonGeneratorEx}:
 * <pre>{@code
 * Protobuf.stream(Protobuf.parser(schema, "Person")).into(ProtobufJson.sink(jsonGenerator));
 * }</pre></li>
 * <li><b>JSON → protobuf</b> — {@link #stream(JsonParserEx, ProtobufSchema, String)} returns a
 * {@link ProtobufStream} pumped by a {@link JsonParserEx}, mapping each JSON value onto its descriptor field
 * so the terminal wire {@link ProtobufSink} encodes it:
 * <pre>{@code
 * ProtobufJson.stream(jsonParser, schema, "Person")
 *     .into(ProtobufSink.of(generator, schema, "Person"));
 * }</pre></li>
 * </ul>
 * Bounded-buffer contract: a JSON document is parsed as a single, fully-buffered value (the engine delivers
 * the reassembled payload), so processing is bounded by the message size and nesting depth; no unbounded
 * document is buffered.
 */
public final class ProtobufJson
{
    /**
     * A terminal {@link ProtobufSink} that writes the protobuf event stream out as JSON through
     * {@code generator}, applying the proto3 JSON mapping. The generator must already be wrapped over its
     * output buffer; read its {@link JsonGeneratorEx#length()} after the pipeline reports
     * {@link io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status#COMPLETED}.
     */
    public static ProtobufSink sink(
        JsonGeneratorEx generator)
    {
        return new ProtobufJsonSink(generator);
    }

    /**
     * Begins a {@link ProtobufStream} pumped by {@code parser}: each JSON value is mapped onto the field of
     * the message named {@code messageName} in {@code schema} (by proto3 json name then proto name) and
     * emitted as the matching protobuf event, so a terminal wire {@link ProtobufSink#of(
     * io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator, ProtobufSchema, String)} encodes it.
     * Append stages with {@link ProtobufStream#transform} and terminate with {@link ProtobufStream#into}.
     */
    public static ProtobufStream stream(
        JsonParserEx parser,
        ProtobufSchema schema,
        String messageName)
    {
        return Protobuf.stream(new JsonProtobufParser(parser, schema, messageName));
    }

    private ProtobufJson()
    {
    }
}
