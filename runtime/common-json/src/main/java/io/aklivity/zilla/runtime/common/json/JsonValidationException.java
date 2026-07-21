/*
 * Copyright 2021-2026 Aklivity Inc
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
import java.util.stream.Collectors;

import jakarta.json.JsonException;

/**
 * Thrown by a validating {@link jakarta.json.stream.JsonParser} produced via
 * {@link JsonSchema#newParser(boolean, jakarta.json.stream.JsonParser)} when the instance
 * event stream fails to conform to the schema and the parser was created in throwing mode.
 * The {@link #diagnostics()} carry the failing keywords and instance JSON-Pointers gathered
 * during the single pass that detected the failure.
 */
public class JsonValidationException extends JsonException
{
    private static final long serialVersionUID = 1L;

    private final transient List<JsonSchemaDiagnostic> diagnostics;

    public JsonValidationException(
        List<JsonSchemaDiagnostic> diagnostics)
    {
        super(message(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<JsonSchemaDiagnostic> diagnostics()
    {
        return diagnostics;
    }

    private static String message(
        List<JsonSchemaDiagnostic> diagnostics)
    {
        return diagnostics.isEmpty()
            ? "JSON instance does not conform to schema"
            : diagnostics.stream().map(JsonSchemaDiagnostic::toString).collect(Collectors.joining("; "));
    }
}
