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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaValidatingParserTest
{
    @Test
    void shouldParseValidInstanceWithoutThrowing()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        JsonParser parser = schema.newParser(true, parserFor("{\"name\":\"hi\"} "));
        drain(parser);
    }

    @Test
    void shouldThrowOnInvalidInstanceWhenThrowing()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonParser parser = schema.newParser(true, parserFor("5 "));
        JsonValidationException ex = assertThrows(JsonValidationException.class, () -> drain(parser));
        assertFalse(ex.diagnostics().isEmpty());
        assertEquals("type", ex.diagnostics().get(0).keyword());
    }

    @Test
    void shouldThrowOnInvalidNestedInstanceWhenThrowing()
    {
        String text = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\"," +
            "\"properties\":{\"b\":{\"type\":\"integer\"}}}}}";
        JsonSchema schema = JsonSchema.of(text);
        JsonParser parser = schema.newParser(true, parserFor("{\"a\":{\"b\":\"oops\"}} "));
        JsonValidationException ex = assertThrows(JsonValidationException.class, () -> drain(parser));
        boolean hasNested = ex.diagnostics().stream()
            .anyMatch(d -> "type".equals(d.keyword()) && "/a/b".equals(d.pointer()));
        assertTrue(hasNested, "expected diagnostic at /a/b, got " + ex.diagnostics());
    }

    @Test
    void shouldReportDiagnosticsWithoutThrowingWhenNotThrowing()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonParser parser = schema.newParser(false, parserFor("5 "), diagnostics::add);
        drain(parser);
        assertEquals(1, diagnostics.size());
        assertEquals("type", diagnostics.get(0).keyword());
    }

    @Test
    void shouldReportAndThrowWhenThrowingWithReporter()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonParser parser = schema.newParser(true, parserFor("5 "), diagnostics::add);
        JsonValidationException ex = assertThrows(JsonValidationException.class, () -> drain(parser));
        assertEquals(diagnostics, ex.diagnostics());
        assertFalse(diagnostics.isEmpty());
    }

    @Test
    void shouldNotReportWhenValidAndNotThrowing()
    {
        List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonParser parser = schema.newParser(false, parserFor("\"hi\" "), diagnostics::add);
        drain(parser);
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void shouldNotThrowOnInvalidWhenNotThrowingWithoutReporter()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonParser parser = schema.newParser(false, parserFor("5 "));
        drain(parser);
    }

    @Test
    void shouldDelegateParserReadsDuringValidation()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"integer\"}}}");
        JsonParser parser = schema.newParser(true, parserFor("{\"count\":42} "));
        List<String> keys = new ArrayList<>();
        int value = 0;
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME)
            {
                keys.add(parser.getString());
            }
            else if (event == JsonParser.Event.VALUE_NUMBER)
            {
                value = parser.getInt();
            }
        }
        assertEquals(List.of("count"), keys);
        assertEquals(42, value);
    }

    private static void drain(
        JsonParser parser)
    {
        while (parser.hasNext())
        {
            parser.next();
        }
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
