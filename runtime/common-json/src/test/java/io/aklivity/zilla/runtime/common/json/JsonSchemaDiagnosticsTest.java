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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaDiagnosticsTest
{
    @Test
    void shouldReportTypeFailureWithKeywordAndPointer()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"type\":\"string\"}")
            .validate(parserFor("5 "), diagnostics::add);
        assertFalse(valid);
        assertEquals(1, diagnostics.size());
        JsonSchemaDiagnostic d = diagnostics.get(0);
        assertEquals("type", d.keyword());
        assertEquals("", d.pointer());
        assertTrue(d.message().contains("string"), d.message());
    }

    @Test
    void shouldReportNestedPropertyPointer()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"type\":\"object\",\"properties\":" +
            "{\"a\":{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"integer\"}}}}}";
        boolean valid = JsonSchema.of(schema)
            .validate(parserFor("{\"a\":{\"b\":\"oops\"}} "), diagnostics::add);
        assertFalse(valid);
        boolean hasNested = diagnostics.stream()
            .anyMatch(d -> "type".equals(d.keyword()) && "/a/b".equals(d.pointer()));
        assertTrue(hasNested, "expected diagnostic at /a/b, got " + diagnostics);
    }

    @Test
    void shouldReportArrayIndexPointer()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}";
        boolean valid = JsonSchema.of(schema)
            .validate(parserFor("[1,2,\"oops\"] "), diagnostics::add);
        assertFalse(valid);
        boolean hasIndex = diagnostics.stream()
            .anyMatch(d -> "type".equals(d.keyword()) && "/2".equals(d.pointer()));
        assertTrue(hasIndex, "expected diagnostic at /2, got " + diagnostics);
    }

    @Test
    void shouldReportRequiredKeyword()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"required\":[\"a\",\"b\"]}")
            .validate(parserFor("{\"a\":1} "), diagnostics::add);
        assertFalse(valid);
        assertTrue(diagnostics.stream().anyMatch(d -> "required".equals(d.keyword())),
            "expected required keyword, got " + diagnostics);
    }

    @Test
    void shouldReportMinMaxBounds()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        JsonSchema.of("{\"minimum\":5}").validate(parserFor("3 "), diagnostics::add);
        assertTrue(diagnostics.stream().anyMatch(d -> "minimum".equals(d.keyword())));

        diagnostics.clear();
        JsonSchema.of("{\"maxLength\":2}").validate(parserFor("\"abcd\" "), diagnostics::add);
        assertTrue(diagnostics.stream().anyMatch(d -> "maxLength".equals(d.keyword())));
    }

    @Test
    void shouldReportCombinatorFailure()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}")
            .validate(parserFor("true "), diagnostics::add);
        assertFalse(valid);
        assertTrue(diagnostics.stream().anyMatch(d -> "oneOf".equals(d.keyword())),
            "expected oneOf diagnostic, got " + diagnostics);
    }

    @Test
    void shouldNotReportFailingAnyOfBranchWhenAnotherMatches()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}")
            .validate(parserFor("5 "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldNotReportFailingOneOfBranchesWhenExactlyOneMatches()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}")
            .validate(parserFor("5 "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldNotReportFailingNotSubschemaWhenInstanceDiffers()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"not\":{\"type\":\"string\"}}")
            .validate(parserFor("5 "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldNotReportFailingIfConditionWhenElseBranchMatches()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"if\":{\"type\":\"string\"},\"then\":{\"minLength\":3}," +
            "\"else\":{\"type\":\"integer\"}}";
        boolean valid = JsonSchema.of(schema)
            .validate(parserFor("5 "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldReportSelectedThenBranchFailureWithoutElseLeak()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"if\":{\"type\":\"string\"},\"then\":{\"minLength\":5}," +
            "\"else\":{\"type\":\"integer\"}}";
        boolean valid = JsonSchema.of(schema)
            .validate(parserFor("\"ab\" "), diagnostics::add);
        assertFalse(valid);
        assertTrue(diagnostics.stream().anyMatch(d -> "then".equals(d.keyword())),
            "expected then diagnostic, got " + diagnostics);
        assertTrue(diagnostics.stream().noneMatch(d -> "type".equals(d.keyword())),
            "expected no leaked else type diagnostic, got " + diagnostics);
    }

    @Test
    void shouldNotReportDependentSchemaWhenTriggerAbsent()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"dependentSchemas\":{\"credit_card\":" +
            "{\"properties\":{\"billing_address\":{\"type\":\"string\"}}," +
            "\"required\":[\"billing_address\"]}}}";
        boolean valid = JsonSchema.of(schema)
            .validate(parserFor("{\"name\":\"x\"} "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldFormatToStringAsLineColPointerMessage()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        JsonSchema.of("{\"type\":\"string\"}").validate(parserFor("42 "), diagnostics::add);
        JsonSchemaDiagnostic d = diagnostics.get(0);
        assertEquals(String.format("[%d,%d][%s] %s", d.line(), d.column(), d.pointer(), d.message()),
            d.toString());
    }

    @Test
    void shouldEscapePointerSegmentTilde()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"properties\":{\"a~b\":{\"type\":\"integer\"}}}";
        JsonSchema.of(schema).validate(parserFor("{\"a~b\":\"x\"} "), diagnostics::add);
        assertTrue(diagnostics.stream().anyMatch(d -> "/a~0b".equals(d.pointer())),
            "expected escaped pointer /a~0b, got " + diagnostics);
    }

    @Test
    void shouldEscapePointerSegmentSlash()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        String schema = "{\"properties\":{\"a/b\":{\"type\":\"integer\"}}}";
        JsonSchema.of(schema).validate(parserFor("{\"a/b\":\"x\"} "), diagnostics::add);
        assertTrue(diagnostics.stream().anyMatch(d -> "/a~1b".equals(d.pointer())),
            "expected escaped pointer /a~1b, got " + diagnostics);
    }

    @Test
    void shouldNotReportWhenValid()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        boolean valid = JsonSchema.of("{\"type\":\"integer\"}")
            .validate(parserFor("42 "), diagnostics::add);
        assertTrue(valid);
        assertTrue(diagnostics.isEmpty(), "expected no diagnostics, got " + diagnostics);
    }

    @Test
    void shouldExposeAllFieldsAndEqualsContract()
    {
        JsonSchemaDiagnostic a = new JsonSchemaDiagnostic(1L, 2L, "/x", "type", "msg");
        JsonSchemaDiagnostic b = new JsonSchemaDiagnostic(1L, 2L, "/x", "type", "msg");
        JsonSchemaDiagnostic c = new JsonSchemaDiagnostic(1L, 2L, "/y", "type", "msg");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a diagnostic");
        assertEquals(a, a);
        assertEquals(1L, a.line());
        assertEquals(2L, a.column());
        assertEquals("/x", a.pointer());
        assertEquals("type", a.keyword());
        assertEquals("msg", a.message());
        assertNotNull(a.toString());
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return JsonEx.createParser(in);
    }
}
