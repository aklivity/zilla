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
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroStream;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * The {@code common-avro} ↔ {@code common-json} bridge: the single place that composes the two
 * self-contained streaming libraries so a datum can cross between Avro binary and JSON without
 * buffering a whole document.
 * <ul>
 * <li>{@link #stream(AvroSchema, JsonParserEx)} — <b>JSON → Avro</b>: walks the compiled schema in
 * lockstep with the JSON pull events from {@code parser}, presenting them as an {@link AvroStream} whose
 * driver emits the Avro event stream. Terminate with {@link AvroStream#into(AvroSink)} over an
 * {@link AvroSink#of(io.aklivity.zilla.runtime.common.avro.AvroGenerator) Avro generator sink} to write
 * Avro binary.</li>
 * <li>{@link #sink(JsonGeneratorEx)} — <b>Avro → JSON</b>: a terminal {@link AvroSink} that materializes
 * each fed Avro event into the corresponding write on {@code generator}, applying the type mapping below.
 * Drive it from an {@link Avro#stream(io.aklivity.zilla.runtime.common.avro.AvroParser) Avro parse
 * pipeline}.</li>
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
     * Begins a <b>JSON → Avro</b> push pipeline driven by {@code parser}: the returned {@link AvroStream}
     * walks {@code schema} in lockstep with the JSON pull events and emits the Avro event stream. Append
     * stages with {@link AvroStream#transform} and terminate with {@link AvroStream#into(AvroSink)}; the
     * {@code parser} (from {@link JsonParserEx}) is borrowed each datum via the pipeline's {@code feed}.
     */
    public static AvroStream stream(
        AvroSchema schema,
        JsonParserEx parser)
    {
        return Avro.stream(new AvroJsonParser(schema, parser));
    }

    /**
     * Returns a terminal <b>Avro → JSON</b> {@link AvroSink} that materializes each fed Avro event into the
     * corresponding write on {@code generator}, applying the documented type mapping. The supplied generator
     * must already be wrapped over its target buffer; reuse a single instance per worker thread.
     */
    public static AvroSink sink(
        JsonGeneratorEx generator)
    {
        return new AvroJsonSink(generator);
    }

    /**
     * The JSON object key the Avro JSON encoding uses for {@code branch} of a union — the primitive type
     * name, the declared name of a named type, or {@code "array"} / {@code "map"}.
     */
    static String branchName(
        AvroType branch)
    {
        AvroKind kind = branch.kind();
        String name;
        switch (kind)
        {
        case NULL:
            name = "null";
            break;
        case BOOLEAN:
            name = "boolean";
            break;
        case INT:
            name = "int";
            break;
        case LONG:
            name = "long";
            break;
        case FLOAT:
            name = "float";
            break;
        case DOUBLE:
            name = "double";
            break;
        case BYTES:
            name = "bytes";
            break;
        case STRING:
            name = "string";
            break;
        case ARRAY:
            name = "array";
            break;
        case MAP:
            name = "map";
            break;
        default:
            name = branch.name();
            break;
        }
        return name;
    }

    /**
     * The branch index of {@code union} whose key matches {@code name} (the Avro JSON union discriminator),
     * or {@code -1} when no branch matches.
     */
    static int branchIndex(
        AvroType union,
        String name)
    {
        int index = -1;
        int count = union.branches().size();
        for (int i = 0; index < 0 && i < count; i++)
        {
            if (branchName(union.branches().get(i)).equals(name))
            {
                index = i;
            }
        }
        return index;
    }

    /**
     * The branch index of {@code union} whose kind is {@code null}, or {@code -1} when the union has no
     * null branch.
     */
    static int nullBranchIndex(
        AvroType union)
    {
        int index = -1;
        int count = union.branches().size();
        for (int i = 0; index < 0 && i < count; i++)
        {
            if (union.branches().get(i).kind() == AvroKind.NULL)
            {
                index = i;
            }
        }
        return index;
    }
}
