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
package io.aklivity.zilla.runtime.common.json;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.json.internal.JsonSchemaImpl;

/**
 * A compiled, immutable JSON Schema validating a streaming {@link JsonParser} event stream
 * without materialising a DOM. Compile once per schema and reuse for the lifetime of the
 * binding; each {@link #validate(JsonParser)} call creates a per-call evaluator and may be
 * called repeatedly on the owning worker thread.
 * <p>
 * Drafts 04, 06, and 07 are supported; {@link Draft} selects the dialect and is otherwise
 * auto-detected from a top-level {@code $schema} URI (defaulting to draft-07).
 * <p>
 * Diagnostics, when requested via the {@link Consumer} overload, are produced at parity with
 * leadpony justify's {@code Problem} output ({@code "[line,col][pointer] message"}), with the
 * failing keyword carried alongside the instance JSON-Pointer.
 */
public interface JsonSchema
{
    enum Draft
    {
        DRAFT_04, DRAFT_06, DRAFT_07, DRAFT_2019_09, DRAFT_2020_12
    }

    static JsonSchema of(
        String schema)
    {
        return JsonSchemaImpl.of(schema);
    }

    static JsonSchema of(
        String schema,
        JsonRefResolver resolver)
    {
        return JsonSchemaImpl.of(schema, resolver);
    }

    static JsonSchema of(
        String schema,
        Draft draft)
    {
        return JsonSchemaImpl.of(schema, draft);
    }

    static JsonSchema of(
        String schema,
        JsonRefResolver resolver,
        Draft draft)
    {
        return JsonSchemaImpl.of(schema, resolver, draft);
    }

    static Set<String> collectRefs(
        String schema)
    {
        return JsonSchemaImpl.collectRefs(schema);
    }

    boolean validate(
        JsonParser parser);

    boolean validate(
        JsonParser parser,
        Consumer<JsonSchemaDiagnostic> reporter);

    /**
     * Wraps {@code parser} in a validating {@link JsonParser} that validates the delegated event
     * stream against this schema in a single pass as the caller pulls events. This replaces the
     * justify {@code JsonValidationService.createJsonProvider(schema, throwing).createParser(...)}
     * approach: the schema is compiled once and the returned parser carries no per-call setup
     * beyond a fresh evaluator.
     * <p>
     * When {@code throwing} is {@code true}, a {@link JsonValidationException} is raised from
     * {@link JsonParser#next()} as soon as the instance is determined not to conform. When
     * {@code throwing} is {@code false}, the stream is validated silently and the failure is
     * observable only via the {@link Consumer} overload.
     */
    JsonParser newParser(
        boolean throwing,
        JsonParser parser);

    /**
     * Variant of {@link #newParser(boolean, JsonParser)} that additionally reports each failing
     * keyword + instance JSON-Pointer + message to {@code reporter} as it is detected, at parity
     * with {@link #validate(JsonParser, Consumer)}. When {@code throwing} is {@code true} the
     * reported diagnostics are also carried by the {@link JsonValidationException}.
     */
    JsonParser newParser(
        boolean throwing,
        JsonParser parser,
        Consumer<JsonSchemaDiagnostic> reporter);

    /**
     * Returns a {@link JsonTransform} stage that validates the event stream against this schema
     * while forwarding every event unchanged to the downstream sink. As a transparent emitter it
     * lets a downstream projector see the complete, unpruned stream (required for whole-value
     * keywords like {@code required}, {@code additionalProperties}, and the combinators); the
     * stage's own {@link JsonPipeline.Status} reflects the schema verdict — {@link
     * JsonPipeline.Status#COMPLETED} when the value validates, {@link
     * JsonPipeline.Status#REJECTED} when it does not. Rejection is reported at the value boundary,
     * after the forwarded events have already been emitted, so callers abort the output stream on
     * {@code REJECTED} (emit-then-abort).
     */
    JsonTransform validator();

    /**
     * Returns the RFC 6901 JSON Pointers to retain when projecting an instance of this schema — the
     * union of paths declared across all branches of the schema. Suitable for {@link
     * StreamingJson#projector(java.util.List)}.
     */
    List<String> retainedPaths();
}
