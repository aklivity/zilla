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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonException;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

class YamlJsonGeneratorTest
{
    @Test
    void shouldGenerateYamlUsingJsonGeneratorApi()
    {
        StringWriter out = new StringWriter();
        JsonGenerator generator = YamlJson.createGenerator(out);

        generator.writeStartObject()
            .write("name", "test")
            .writeStartObject("bindings")
                .writeStartObject("test0")
                    .write("type", "test")
                    .write("kind", "server")
                    .writeStartArray("routes")
                        .writeStartObject()
                            .write("exit", "exit0")
                            .writeStartArray("when")
                                .writeStartObject()
                                    .write("match", "test")
                                .writeEnd()
                            .writeEnd()
                        .writeEnd()
                    .writeEnd()
                .writeEnd()
            .writeEnd()
            .writeEnd()
            .close();

        assertEquals("""
            name: test
            bindings:
              test0:
                type: test
                kind: server
                routes:
                  - exit: exit0
                    when:
                      - match: test
            """, out.toString());
    }

    @Test
    void shouldGenerateScalarsArraysAndObjects()
    {
        StringWriter out = new StringWriter();

        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("quoted", "2.0")
                .write("integer", 42)
                .write("decimal", new BigDecimal("3.14"))
                .write("enabled", true)
                .writeNull("missing")
                .writeStartArray("items")
                    .write("alpha")
                    .writeStartArray()
                        .write(1)
                    .writeEnd()
                .writeEnd()
            .writeEnd()
            .close();

        assertEquals("""
            quoted: "2.0"
            integer: 42
            decimal: 3.14
            enabled: true
            missing: null
            items:
              - alpha
              -
                - 1
            """, out.toString());
    }

    @Test
    void shouldQuoteNumericStringsAndLeaveAmbiguousStringsPlain()
    {
        StringWriter out = new StringWriter();

        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("zero", "0")
                .write("integer", "42")
                .write("exponent", "1e10")
                .write("signedExponent", "1.5e-3")
                .write("leadingZero", "007")
                .write("address", "0.0.0.0")
                .write("version", "1.2.3")
                .write("word", "server")
            .writeEnd()
            .close();

        assertEquals("""
            zero: "0"
            integer: "42"
            exponent: "1e10"
            signedExponent: "1.5e-3"
            leadingZero: 007
            address: 0.0.0.0
            version: 1.2.3
            word: server
            """, out.toString());
    }

    @Test
    void shouldQuoteAndEscapeSpecialCharacters()
    {
        StringWriter out = new StringWriter();

        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("v", "a\"b\\c\nd\te\bf\fg\rh")
            .writeEnd()
            .close();

        assertEquals("v: \"a\\\"b\\\\c\\nd\\te\\bf\\fg\\rh\"\n", out.toString());
    }

    @Test
    void shouldGenerateIntegerScalarEdges()
    {
        StringWriter out = new StringWriter();

        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("zero", 0)
                .write("negative", -7114)
                .write("intMin", Integer.MIN_VALUE)
                .write("longMin", Long.MIN_VALUE)
                .write("longMax", Long.MAX_VALUE)
            .writeEnd()
            .close();

        assertEquals("""
            zero: 0
            negative: -7114
            intMin: -2147483648
            longMin: -9223372036854775808
            longMax: 9223372036854775807
            """, out.toString());
    }

    @Test
    void shouldGenerateObjectsNestedBeyondInitialStackCapacity()
    {
        int depth = 12;

        StringWriter out = new StringWriter();
        JsonGenerator generator = YamlJson.createGenerator(out);

        generator.writeStartObject();
        for (int level = 0; level < depth; level++)
        {
            generator.writeStartObject("k" + level);
        }
        generator.write("leaf", "v");
        for (int level = 0; level <= depth; level++)
        {
            generator.writeEnd();
        }
        generator.close();

        StringBuilder expected = new StringBuilder();
        for (int level = 0; level < depth; level++)
        {
            expected.append("  ".repeat(level)).append('k').append(level).append(":\n");
        }
        expected.append("  ".repeat(depth)).append("leaf: v\n");

        assertEquals(expected.toString(), out.toString());
    }

    @Test
    void shouldGenerateFromJsonValues()
    {
        StringWriter out = new StringWriter();
        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("empty", JsonValue.EMPTY_JSON_OBJECT)
                .write("items", JsonValue.EMPTY_JSON_ARRAY)
                .write("flag", JsonValue.TRUE)
                .writeNull("missing")
            .writeEnd()
            .close();

        assertEquals("""
            empty: {}
            items: []
            flag: true
            missing: null
            """, out.toString());
    }

    @Test
    void shouldCreateGeneratorFromFactory()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGeneratorFactory factory = YamlJson.createGeneratorFactory(Map.of("test", "value"));

        factory.createGenerator(out, UTF_8)
            .writeStartObject()
                .write("name", "test")
            .writeEnd()
            .close();

        assertEquals(Map.of("test", "value"), factory.getConfigInUse());
        assertEquals("name: test\n", out.toString(UTF_8));
    }

    @Test
    void shouldGenerateJsonAsYamlWhenYamlOnlyFeaturesAreDisabled()
    {
        StringWriter out = new StringWriter();
        JsonGeneratorFactory factory = YamlJson.createGeneratorFactory(Map.ofEntries(
            Map.entry(YamlConfig.FEATURE_DIRECTIVES, false),
            Map.entry(YamlConfig.FEATURE_DOCUMENT_MARKERS, false),
            Map.entry(YamlConfig.FEATURE_BLOCK_SCALARS, false),
            Map.entry(YamlConfig.FEATURE_FLOW_COLLECTIONS, false),
            Map.entry(YamlConfig.FEATURE_ANCHORS, false),
            Map.entry(YamlConfig.FEATURE_ALIASES, false),
            Map.entry(YamlConfig.FEATURE_MERGE_KEYS, false),
            Map.entry(YamlConfig.FEATURE_TAGS, false),
            Map.entry(YamlConfig.FEATURE_COMMENTS, false),
            Map.entry(YamlConfig.FEATURE_NON_SCALAR_KEYS, false),
            Map.entry(YamlConfig.FEATURE_MULTI_DOCUMENT_STREAMS, false)));

        factory.createGenerator(out)
            .writeStartObject()
                .write("name", "test")
                .write("count", 1)
                .write("enabled", true)
                .writeStartArray("items")
                    .write("one")
                    .write(2)
                .writeEnd()
            .writeEnd()
            .close();

        assertEquals("""
            name: test
            count: 1
            enabled: true
            items:
              - one
              - 2
            """, out.toString());
    }

    @Test
    void shouldGenerateViaOutputStreamEntryPointsAndFlush()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator generator = YamlJson.createGenerator(out);

        generator.writeStartArray()
            .write(1L)
            .write(new BigInteger("2"))
            .write(3.0)
            .writeEnd()
            .flush();

        assertEquals("""
            - 1
            - 2
            - 3.0
            """, out.toString(UTF_8));
        assertThrows(JsonException.class, () -> generator.write(4));
        generator.close();

        out = new ByteArrayOutputStream();
        YamlJson.createGeneratorFactory(Map.of())
            .createGenerator(out)
            .write(JsonValue.TRUE)
            .close();
        assertEquals("true\n", out.toString(UTF_8));
    }

    @Test
    void shouldGenerateEmptyRootValues()
    {
        StringWriter out = new StringWriter();
        YamlJson.createGenerator(out).write(JsonValue.EMPTY_JSON_OBJECT).close();
        assertEquals("{}\n", out.toString());

        out = new StringWriter();
        YamlJson.createGenerator(out).write(JsonValue.EMPTY_JSON_ARRAY).close();
        assertEquals("[]\n", out.toString());
    }

    @Test
    void shouldRoundTripGeneratedYamlThroughParser()
    {
        StringWriter out = new StringWriter();
        YamlJson.createGenerator(out)
            .writeStartObject()
                .write("name", "test")
                .writeStartArray("values")
                    .write(1)
                    .write("text")
                    .write(false)
                .writeEnd()
            .writeEnd()
            .close();

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:values",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "VALUE_STRING:text",
            "VALUE_FALSE",
            "END_ARRAY",
            "END_OBJECT"), events(YamlJson.createParser(new StringReader(out.toString()))));
    }

    @Test
    void shouldRejectIncompleteDocuments()
    {
        JsonGenerator generator = YamlJson.createGenerator(new StringWriter());

        generator.writeStartObject().writeKey("name");

        assertThrows(JsonException.class, generator::close);
    }

    @Test
    void shouldRejectInvalidGeneratorState()
    {
        assertThrows(JsonException.class, () -> YamlJson.createGenerator(new StringWriter()).writeKey("name"));
        assertThrows(JsonException.class, () -> YamlJson.createGenerator(new StringWriter()).writeEnd());
        assertThrows(JsonException.class, () -> YamlJson.createGenerator(new StringWriter()).write(Double.NaN));

        JsonGenerator object = YamlJson.createGenerator(new StringWriter()).writeStartObject();
        assertThrows(JsonException.class, () -> object.write("value"));
        assertThrows(JsonException.class, () -> object.writeKey("a").writeKey("b"));

        JsonGenerator rooted = YamlJson.createGenerator(new StringWriter());
        rooted.write(1);
        assertThrows(JsonException.class, () -> rooted.write(2));

        JsonGenerator generator = YamlJson.createGenerator(new StringWriter());
        generator.write(1).close();
        JsonGenerator closed = generator;
        assertThrows(JsonException.class, () -> closed.write(2));
    }

    private static List<String> events(
        JsonParser parser)
    {
        List<String> events = new ArrayList<>();
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
