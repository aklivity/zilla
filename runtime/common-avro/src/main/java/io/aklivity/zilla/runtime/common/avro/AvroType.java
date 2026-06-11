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
package io.aklivity.zilla.runtime.common.avro;

import java.util.List;

/**
 * An immutable, read-only node in a compiled Avro type graph. The {@link #kind()} selects which
 * accessors carry meaning; the others return an empty list, {@code null}, or {@code 0}. The graph is
 * navigable by reference — {@link #fields()}, {@link #items()}, {@link #values()}, and
 * {@link #branches()} return further {@code AvroType} nodes — and a recursive schema is a cyclic graph,
 * so traversal terminates by node identity rather than by a separate name lookup. Obtained from
 * {@link AvroSchema#type()} for the whole schema, or contextually from {@link AvroSource#type()} /
 * {@link AvroParser#type()} for the value at the current event. Read off the hot path; the collection
 * accessors build a fresh view per call.
 */
public interface AvroType
{
    AvroKind kind();

    /**
     * The declared name of a named type ({@link AvroKind#RECORD}, {@link AvroKind#ENUM},
     * {@link AvroKind#FIXED}); {@code null} for anonymous and primitive types.
     */
    String name();

    /**
     * The Avro logical type annotation (for example {@code decimal}, {@code timestamp-millis},
     * {@code uuid}); {@code null} when none is declared.
     */
    String logicalType();

    /**
     * The total number of significant digits of a {@code decimal} logical type; {@code 0} otherwise.
     * Required, with {@link #scale()}, to interpret the unscaled integer a {@link AvroKind#BYTES} or
     * {@link AvroKind#FIXED} decimal carries on the wire.
     */
    int precision();

    /**
     * The number of digits to the right of the decimal point of a {@code decimal} logical type;
     * {@code 0} otherwise (which is also the Avro default scale).
     */
    int scale();

    /**
     * The fields of a {@link AvroKind#RECORD}, in declaration (and wire) order; empty otherwise.
     */
    List<AvroField> fields();

    /**
     * The element type of an {@link AvroKind#ARRAY}; {@code null} otherwise.
     */
    AvroType items();

    /**
     * The value type of a {@link AvroKind#MAP}; {@code null} otherwise.
     */
    AvroType values();

    /**
     * The branch types of a {@link AvroKind#UNION}, in declaration (and branch-index) order; empty
     * otherwise.
     */
    List<AvroType> branches();

    /**
     * The symbols of an {@link AvroKind#ENUM}, in ordinal order; empty otherwise.
     */
    List<String> symbols();

    /**
     * The byte count of a {@link AvroKind#FIXED}; {@code 0} otherwise.
     */
    int size();
}
