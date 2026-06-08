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

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

class YamlJsonParserTest
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
    void shouldParseJsonObjectDocument()
    {
        JsonParser parser = parserFor("""
            {
              "name": "test",
              "enabled": true,
              "missing": null,
              "numbers": [0, -0, -42, 3.14, 1.0e+10, -2E-3],
              "escaped": "quote \\" slash \\/ backslash \\\\ line\\n tab\\t unicode \\u0041",
              "nested": {
                "emptyObject": {},
                "emptyArray": []
              }
            }
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:enabled",
            "VALUE_TRUE",
            "KEY_NAME:missing",
            "VALUE_NULL",
            "KEY_NAME:numbers",
            "START_ARRAY",
            "VALUE_NUMBER:0",
            "VALUE_NUMBER:-0",
            "VALUE_NUMBER:-42",
            "VALUE_NUMBER:3.14",
            "VALUE_NUMBER:1.0e+10",
            "VALUE_NUMBER:-2E-3",
            "END_ARRAY",
            "KEY_NAME:escaped",
            "VALUE_STRING:quote \" slash / backslash \\ line\n tab\t unicode A",
            "KEY_NAME:nested",
            "START_OBJECT",
            "KEY_NAME:emptyObject",
            "START_OBJECT",
            "END_OBJECT",
            "KEY_NAME:emptyArray",
            "START_ARRAY",
            "END_ARRAY",
            "END_OBJECT",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldParseJsonArrayAndScalarDocuments()
    {
        assertEquals(List.of(
            "START_ARRAY",
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "END_OBJECT",
            "VALUE_FALSE",
            "VALUE_NULL",
            "END_ARRAY"), events(parserFor("""
            [
              {"name": "test"},
              false,
              null
            ]
            """)));

        assertEquals(List.of("VALUE_STRING:value"), events(parserFor("\"value\"\n")));
        assertEquals(List.of("VALUE_NUMBER:-12.5e-1"), events(parserFor("-12.5e-1\n")));
        assertEquals(List.of("VALUE_TRUE"), events(parserFor("true\n")));
        assertEquals(List.of("VALUE_FALSE"), events(parserFor("false\n")));
        assertEquals(List.of("VALUE_NULL"), events(parserFor("null\n")));
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
    void shouldParseYamlDoubleQuotedEscapes()
    {
        JsonObject object = YamlJson.provider().createReader(new StringReader("""
            control: "\\0\\a\\b\\t\\n\\v\\f\\r\\e"
            space: "\\ \\_"
            unicode: "\\N\\L\\P\\x41\\u0042\\U00000043"
            astral: "\\U0001f600"
            """)).readObject();

        assertEquals("\0\u0007\b\t\n\u000b\f\r\u001b", object.getString("control"));
        assertEquals(" \u00a0", object.getString("space"));
        assertEquals("\u0085\u2028\u2029ABC", object.getString("unicode"));
        assertEquals(0x1f600, object.getString("astral").codePointAt(0));
    }

    @Test
    void shouldApplyTagDirectivesToCoreTags()
    {
        JsonObject object = YamlJson.provider().createReader(new StringReader("""
            %TAG !yaml! tag:yaml.org,2002:
            ---
            string: !yaml!str 42
            integer: !yaml!int 42
            boolean: !yaml!bool true
            sequence: !yaml!seq [1, 2]
            mapping: !yaml!map {name: test}
            """)).readObject();

        assertEquals("42", object.getString("string"));
        assertEquals(42, object.getInt("integer"));
        assertEquals(true, object.getBoolean("boolean"));
        assertEquals(2, object.getJsonArray("sequence").getInt(1));
        assertEquals("test", object.getJsonObject("mapping").getString("name"));
        assertThrows(JsonParsingException.class, () -> events(parserFor("""
            %TAG !custom! tag:example.com,2026:
            ---
            value: !custom!thing test
            """)));
    }

    @Test
    void shouldResolveNestedMergeAliasesInOrder()
    {
        JsonObject object = YamlJson.provider().createReader(new StringReader("""
            base: &base
              host: localhost
              port: 7114
            tls: &tls
              port: 443
              secure: true
            route:
              <<: [*base, *tls]
              host: example.com
            """)).readObject();

        JsonObject route = object.getJsonObject("route");
        assertEquals("example.com", route.getString("host"));
        assertEquals(7114, route.getInt("port"));
        assertEquals(true, route.getBoolean("secure"));
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
    void shouldParseDocumentMarkersDirectivesAndExposeNextDocumentLocation()
    {
        String text = """
            %YAML 1.2
            ---
            name: one
            ---
            name: two
            """;
        JsonParser parser = parserFor(text);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:one",
            "END_OBJECT"), events(parser));
        assertEquals(text.indexOf("---", text.indexOf("---") + 1), parser.getLocation().getStreamOffset());
    }

    @Test
    void shouldParseMultiDocumentStreamWithRepeatedParsers()
    {
        String text = """
            ---
            name: one
            ...
            ---
            name: two
            values: [1, 2]
            """;
        int documentAt = 0;

        JsonParser parser = parserFor(text.substring(documentAt));
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:one",
            "END_OBJECT"), events(parser));

        documentAt += (int) parser.getLocation().getStreamOffset();
        assertEquals(text.indexOf("---", text.indexOf("...")), documentAt);

        parser = parserFor(text.substring(documentAt));
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:two",
            "KEY_NAME:values",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "VALUE_NUMBER:2",
            "END_ARRAY",
            "END_OBJECT"), events(parser));

        documentAt += (int) parser.getLocation().getStreamOffset();
        assertEquals(text.length(), documentAt);
    }

    @Test
    void shouldParseBlockScalarsAndMultiLineFlowCollections()
    {
        JsonParser parser = parserFor("""
            description: |
              alpha
              beta
            summary: >
              folded
              line
            values: [
              1,
              {name: test, flag: true}
            ]
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:description",
            "VALUE_STRING:alpha\nbeta\n",
            "KEY_NAME:summary",
            "VALUE_STRING:folded line\n",
            "KEY_NAME:values",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:flag",
            "VALUE_TRUE",
            "END_OBJECT",
            "END_ARRAY",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldResolveAnchorsAliasesMergeKeysCoreTagsAndExplicitScalarKeys()
    {
        JsonParser parser = parserFor("""
            defaults: &defaults
              type: test
              enabled: true
            binding:
              <<: *defaults
              enabled: false
              port: !!int "7143"
            ? "quoted key"
            : !!str 42
            items:
              - &item {name: one, value: !!float "1.5"}
              - *item
            """);

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:defaults",
            "START_OBJECT",
            "KEY_NAME:type",
            "VALUE_STRING:test",
            "KEY_NAME:enabled",
            "VALUE_TRUE",
            "END_OBJECT",
            "KEY_NAME:binding",
            "START_OBJECT",
            "KEY_NAME:type",
            "VALUE_STRING:test",
            "KEY_NAME:enabled",
            "VALUE_FALSE",
            "KEY_NAME:port",
            "VALUE_NUMBER:7143",
            "END_OBJECT",
            "KEY_NAME:quoted key",
            "VALUE_STRING:42",
            "KEY_NAME:items",
            "START_ARRAY",
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:one",
            "KEY_NAME:value",
            "VALUE_NUMBER:1.5",
            "END_OBJECT",
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:one",
            "KEY_NAME:value",
            "VALUE_NUMBER:1.5",
            "END_OBJECT",
            "END_ARRAY",
            "END_OBJECT"), events(parser));
    }

    @Test
    void shouldCreateExplicitYamlJsonProviderWithServiceRegistration()
    {
        JsonProvider provider = YamlJson.provider();
        JsonParser parser = provider.createParser(new StringReader("name: test\n"));

        assertEquals("YamlJsonProvider", provider.getClass().getSimpleName());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("name", parser.getString());
        assertEquals(provider.getClass(), JsonProvider.provider().getClass());
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
        JsonParserFactory factory = YamlJson.createParserFactory(Map.of("test", "value"));
        JsonParser parser = factory.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8)));

        assertEquals(Map.of("test", "value"), factory.getConfigInUse());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("name", parser.getString());

        parser = factory.createParser(new StringReader("name: test\n"));
        assertEquals(START_OBJECT, parser.next());

        parser = factory.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8)), UTF_8);
        assertEquals(START_OBJECT, parser.next());

        parser = factory.createParser(JsonValue.EMPTY_JSON_OBJECT);
        assertEquals(START_OBJECT, parser.next());
        assertEquals(END_OBJECT, parser.next());

        parser = factory.createParser(JsonValue.EMPTY_JSON_ARRAY);
        assertEquals(START_ARRAY, parser.next());
        assertEquals(END_ARRAY, parser.next());
    }

    @Test
    void shouldExposeParserMethods()
    {
        JsonParser parser = parserFor("""
            name: test
            values: [1]
            """);

        assertThrows(IllegalStateException.class, parser::currentEvent);
        assertThrows(IllegalStateException.class, parser::getValue);
        assertEquals(START_OBJECT, parser.next());
        assertEquals(START_OBJECT, parser.currentEvent());
        JsonObject object = parser.getObject();
        assertEquals("test", object.getString("name"));
        assertEquals(1, object.getJsonArray("values").getInt(0));
        assertEquals(2, parser.getObjectStream().count());
        assertEquals(1, parser.getValueStream().count());
        assertThrows(IllegalStateException.class, parser::skipArray);

        while (parser.hasNext())
        {
            parser.next();
        }
        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldSkipNestedObject()
    {
        JsonParser parser = parserFor("""
            name: test
            skip:
              child:
                - 1
                - nested: true
            after: done
            """);

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("name", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("test", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("skip", parser.getString());
        assertEquals(START_OBJECT, parser.next());

        parser.skipObject();

        assertEquals(END_OBJECT, parser.currentEvent());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("after", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("done", parser.getString());
        assertEquals(END_OBJECT, parser.next());
    }

    @Test
    void shouldSkipNestedArray()
    {
        JsonParser parser = parserFor("""
            [
              {"name": "skip", "values": [1, {"nested": true}]},
              "after"
            ]
            """);

        assertEquals(START_ARRAY, parser.next());
        assertEquals(START_OBJECT, parser.next());

        parser.skipObject();

        assertEquals(END_OBJECT, parser.currentEvent());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("after", parser.getString());
        assertEquals(END_ARRAY, parser.next());

        parser = parserFor("[{\"name\":\"skip\",\"values\":[1,{\"nested\":true}]},\"after\"]\n");
        assertEquals(START_ARRAY, parser.next());
        parser.skipArray();
        assertEquals(END_ARRAY, parser.currentEvent());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldRejectUnsupportedYamlFeaturesAndMalformedDocuments()
    {
        assertThrows(JsonParsingException.class, () -> events(parserFor("value: !custom test\n")));
        assertThrows(JsonParsingException.class, () -> parserFor("value: *missing\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("{[key]: value}\n"));
        assertThrows(JsonParsingException.class, () -> events(parserFor("? [key]\n: value\n")));
        assertThrows(JsonParsingException.class, () -> parserFor("value: .nan\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("value: |\ntext\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("\tname: test\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("name: \"test\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("values: [1, 2\n"));
        assertThrows(JsonParsingException.class, () -> parserFor("value: \"\\x\"\n"));
    }

    private static JsonParser parserFor(
        String text)
    {
        return YamlJson.createParser(new StringReader(text));
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
