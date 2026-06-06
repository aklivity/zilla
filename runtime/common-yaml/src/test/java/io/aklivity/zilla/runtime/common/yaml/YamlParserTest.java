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
package io.aklivity.zilla.runtime.common.yaml;

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_FALSE;
import static jakarta.json.stream.JsonParser.Event.VALUE_NULL;
import static jakarta.json.stream.JsonParser.Event.VALUE_NUMBER;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static jakarta.json.stream.JsonParser.Event.VALUE_TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

class YamlParserTest
{
    @Test
    void shouldParseBlockMappingsAndIndentlessSequences()
    {
        JsonParser parser = parserFor("""
            name: test
            bindings:
              test0:
                type: test
                kind: server
                routes:
                - exit: exit0
                  when:
                  - match: test
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:bindings",
            "START_OBJECT",
            "KEY_NAME:test0",
            "START_OBJECT",
            "KEY_NAME:type",
            "VALUE_STRING:test",
            "KEY_NAME:kind",
            "VALUE_STRING:server",
            "KEY_NAME:routes",
            "START_ARRAY",
            "START_OBJECT",
            "KEY_NAME:exit",
            "VALUE_STRING:exit0",
            "KEY_NAME:when",
            "START_ARRAY",
            "START_OBJECT",
            "KEY_NAME:match",
            "VALUE_STRING:test",
            "END_OBJECT",
            "END_ARRAY",
            "END_OBJECT",
            "END_ARRAY",
            "END_OBJECT",
            "END_OBJECT",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldParseFlowCollectionsAndComments()
    {
        JsonParser parser = parserFor("""
            name: test # trailing comment
            values: [1, true, false, null, "a # value", {path: "/a#b"}]
            """);

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("name", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("test", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("values", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(1, parser.getInt());
        assertEquals(VALUE_TRUE, parser.next());
        assertEquals(VALUE_FALSE, parser.next());
        assertEquals(VALUE_NULL, parser.next());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("a # value", parser.getString());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("path", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("/a#b", parser.getString());
        assertEquals(END_OBJECT, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseNumbers()
    {
        JsonParser parser = parserFor("values: [-42, 3.14, 1e10]\n");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(-42, parser.getInt());
        assertEquals(-42L, parser.getLong());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(new BigDecimal("3.14"), parser.getBigDecimal());
        assertEquals(VALUE_NUMBER, parser.next());
        assertFalse(parser.isIntegralNumber());
    }

    @Test
    void shouldParseQuotedScalarsAndEscapes()
    {
        JsonParser parser = parserFor("""
            single: 'it''s'
            double: "line\\n\\u0041"
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:single",
            "VALUE_STRING:it's",
            "KEY_NAME:double",
            "VALUE_STRING:line\nA",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldParseEmptyDocumentValuesAndNestedSequenceItems()
    {
        assertEquals(List.of("VALUE_NULL"), events(parserFor("")));

        JsonParser parser = parserFor("""
            ---
            items:
              -
                name: one
              - two
              - []
              - {}
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:items",
            "START_ARRAY",
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:one",
            "END_OBJECT",
            "VALUE_STRING:two",
            "START_ARRAY",
            "END_ARRAY",
            "START_OBJECT",
            "END_OBJECT",
            "END_ARRAY",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldExposeParserLocations()
    {
        JsonParser parser = parserFor("name: test\n");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(1, parser.getLocation().getLineNumber());
        assertEquals(1, parser.getLocation().getColumnNumber());
        assertEquals(0, parser.getLocation().getStreamOffset());
    }

    @Test
    void shouldCreateParserFromFactory()
    {
        JsonParserFactory factory = Yaml.createParserFactory(Map.of("test", "value"));
        JsonParser parser = factory.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8)));

        assertEquals(Map.of("test", "value"), factory.getConfigInUse());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("name", parser.getString());

        parser = factory.createParser(new StringReader("name: test\n"));
        assertEquals(START_OBJECT, parser.next());

        parser = factory.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8)), UTF_8);
        assertEquals(START_OBJECT, parser.next());

        assertThrows(UnsupportedOperationException.class, () -> factory.createParser(JsonValue.EMPTY_JSON_OBJECT));
        assertThrows(UnsupportedOperationException.class, () -> factory.createParser(JsonValue.EMPTY_JSON_ARRAY));
    }

    @Test
    void shouldExposeUnsupportedParserMethods()
    {
        JsonParser parser = parserFor("name: test\n");

        assertThrows(UnsupportedOperationException.class, parser::getObject);
        assertThrows(UnsupportedOperationException.class, parser::getValue);
        assertThrows(UnsupportedOperationException.class, parser::getArray);
        assertThrows(UnsupportedOperationException.class, parser::getArrayStream);
        assertThrows(UnsupportedOperationException.class, parser::getObjectStream);
        assertThrows(UnsupportedOperationException.class, parser::getValueStream);
        assertThrows(UnsupportedOperationException.class, parser::skipObject);
        assertThrows(UnsupportedOperationException.class, parser::skipArray);

        while (parser.hasNext())
        {
            parser.next();
        }
        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectOutOfScopeYamlFeatures()
    {
        assertThrows(JsonParsingException.class, () -> parserFor("value: &anchor test\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("value: |\n  text\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("---\nname: one\n---\nname: two\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("\tname: test\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("name: \"test\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("values: [1, 2\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("value: \"\\x\"\n"));
    }

    private static JsonParser parserFor(
        String text)
    {
        return Yaml.createParser(new StringReader(text));
    }

    private static List<String> events(
        JsonParser parser)
    {
        List<String> events = new java.util.ArrayList<>();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            switch (event)
            {
            case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> events.add(event.name() + ":" + parser.getString());
            default -> events.add(event.name());
            }
        }
        return events;
    }
}
