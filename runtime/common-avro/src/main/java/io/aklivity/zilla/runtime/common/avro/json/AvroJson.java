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
package io.aklivity.zilla.runtime.common.avro.json;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroStream;
import io.aklivity.zilla.runtime.common.avro.internal.json.AvroJsonGeneratorImpl;
import io.aklivity.zilla.runtime.common.avro.internal.json.AvroJsonParserImpl;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * The {@code common-avro} ↔ {@code common-json} bridge: the single place that composes the two
 * self-contained streaming libraries so a datum can cross between Avro binary and JSON without
 * buffering a whole document. It is symmetric, adapting each {@code common-avro} streaming contract over its
 * {@code common-json} peer so both fit the existing pipeline unchanged:
 * <ul>
 * <li>{@link #parser(AvroSchema, JsonParserEx)} — <b>JSON → Avro</b>: an {@link AvroParser} that drives a
 * {@link JsonParserEx}, presenting the JSON events and accessors as the Avro event stream. Use it as a cursor
 * straight into an {@link AvroGenerator}, or via {@link #stream(AvroSchema, JsonParserEx)} terminated with
 * {@link AvroStream#into(AvroSink)} over an {@link AvroSink#of(AvroGenerator) Avro generator sink} to write
 * Avro binary.</li>
 * <li>{@link #generator(AvroSchema, JsonGeneratorEx)} — <b>Avro → JSON</b>: an {@link AvroGenerator} that
 * drives a {@link JsonGeneratorEx}, mapping each positional Avro write onto JSON. Use it behind an
 * {@link AvroSink#of(AvroGenerator) AvroSink} terminating an
 * {@link Avro#stream(AvroParser) Avro parse pipeline}.</li>
 * </ul>
 * <p>
 * <b>Type mapping (Avro ↔ JSON).</b> {@code null}/{@code boolean}/{@code int}/{@code long}/{@code float}/
 * {@code double} map to the corresponding JSON scalar (a 64-bit {@code long} beyond 2^53 loses precision if
 * a JSON reader narrows it to a double — preserved here because the number lexeme is carried verbatim);
 * {@code string} maps to a JSON string; {@code bytes} and {@code fixed} map to a base64-encoded JSON string;
 * {@code enum} maps to its symbol as a JSON string; a {@code record} maps to a JSON object keyed by field
 * name; a {@code map} maps to a JSON object; an {@code array} maps to a JSON array; a {@code union} maps to
 * {@code null} for its null branch, or to the single-entry object {@code {"<branch>": value}} for any other
 * branch (the Avro JSON encoding), where {@code <branch>} is the primitive type name, or the declared name
 * of a {@code record}/{@code enum}/{@code fixed}, or {@code "array"}/{@code "map"}. Logical types are carried
 * by their underlying representation (for example a {@code decimal} as its base64 {@code bytes}/{@code fixed}
 * unscaled value), so the round trip is byte-exact on the Avro side but does not surface the logical view.
 */
public final class AvroJson
{
    private AvroJson()
    {
    }

    /**
     * Returns a schema-bound <b>JSON → Avro</b> {@link AvroParser} that drives {@code parser}, presenting the
     * JSON pull events and accessors as the Avro event stream. Pass it to {@link Avro#stream(AvroParser)} (or
     * use {@link #stream(AvroSchema, JsonParserEx)}) to build a pipeline, or drive it as a cursor straight
     * into an {@link AvroGenerator}. Reuse a single instance per worker thread.
     */
    public static AvroParser parser(
        AvroSchema schema,
        JsonParserEx parser)
    {
        return new AvroJsonParserImpl(schema, parser);
    }

    /**
     * Variant of {@link #parser(AvroSchema, JsonParserEx)} that, when {@code canonical} is {@code true},
     * reads a nullable-single union (a union of {@code null} and exactly one other type) from a bare JSON
     * value — {@code null} or the unwrapped value — rather than the {@code {"<branch>": value}} wrapper.
     * Every other union shape is unchanged.
     */
    public static AvroParser parser(
        AvroSchema schema,
        JsonParserEx parser,
        boolean canonical)
    {
        return new AvroJsonParserImpl(schema, parser, canonical);
    }

    /**
     * Begins a <b>JSON → Avro</b> push pipeline driven by {@code parser}: the returned {@link AvroStream}
     * walks {@code schema} in lockstep with the JSON pull events and emits the Avro event stream. Append
     * stages with {@link AvroStream#transform} and terminate with {@link AvroStream#into(AvroSink)}; the
     * {@code parser} (from {@link JsonParserEx}) is borrowed each datum via the pipeline's {@code feed}.
     */
    public static AvroStream stream(
        AvroSchema schema,
        JsonParserEx parser)
    {
        return Avro.stream(parser(schema, parser));
    }

    /**
     * Variant of {@link #stream(AvroSchema, JsonParserEx)} with the canonical nullable-single union reading
     * of {@link #parser(AvroSchema, JsonParserEx, boolean)}.
     */
    public static AvroStream stream(
        AvroSchema schema,
        JsonParserEx parser,
        boolean canonical)
    {
        return Avro.stream(parser(schema, parser, canonical));
    }

    /**
     * Returns a schema-bound <b>Avro → JSON</b> {@link AvroGenerator} that maps each positional Avro write
     * onto {@code generator}, applying the documented type mapping. Wrap it over the target buffer via
     * {@link AvroGenerator#wrap} (which re-targets {@code generator}), then drive it directly or behind an
     * {@link AvroSink#of(AvroGenerator) AvroSink}. Reuse a single instance per worker thread.
     */
    public static AvroGenerator generator(
        AvroSchema schema,
        JsonGeneratorEx generator)
    {
        return new AvroJsonGeneratorImpl(schema, generator);
    }

    /**
     * Variant of {@link #generator(AvroSchema, JsonGeneratorEx)} that, when {@code canonical} is
     * {@code true}, writes a nullable-single union (a union of {@code null} and exactly one other type) as a
     * bare JSON value — {@code null} or the unwrapped value — rather than the {@code {"<branch>": value}}
     * wrapper. Every other union shape is unchanged.
     */
    public static AvroGenerator generator(
        AvroSchema schema,
        JsonGeneratorEx generator,
        boolean canonical)
    {
        return new AvroJsonGeneratorImpl(schema, generator, canonical);
    }
}
