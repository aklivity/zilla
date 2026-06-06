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

class YamlGeneratorTest
{
    @Test
    void shouldGenerateYamlUsingJsonGeneratorApi()
    {
        StringWriter out = new StringWriter();
        JsonGenerator generator = Yaml.createGenerator(out);

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

        assertEquals(String.join("\n",
            "name: test",
            "bindings:",
            "  test0:",
            "    type: test",
            "    kind: server",
            "    routes:",
            "      - exit: exit0",
            "        when:",
            "          - match: test",
            ""), out.toString());
    }

    @Test
    void shouldGenerateScalarsArraysAndObjects()
    {
        StringWriter out = new StringWriter();

        Yaml.createGenerator(out)
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

        assertEquals(String.join("\n",
            "quoted: \"2.0\"",
            "integer: 42",
            "decimal: 3.14",
            "enabled: true",
            "missing: null",
            "items:",
            "  - alpha",
            "  -",
            "    - 1",
            ""), out.toString());
    }

    @Test
    void shouldGenerateFromJsonValues()
    {
        StringWriter out = new StringWriter();
        Yaml.createGenerator(out)
            .writeStartObject()
                .write("empty", JsonValue.EMPTY_JSON_OBJECT)
                .write("items", JsonValue.EMPTY_JSON_ARRAY)
                .write("flag", JsonValue.TRUE)
                .writeNull("missing")
            .writeEnd()
            .close();

        assertEquals(String.join("\n",
            "empty: {}",
            "items: []",
            "flag: true",
            "missing: null",
            ""), out.toString());
    }

    @Test
    void shouldCreateGeneratorFromFactory()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGeneratorFactory factory = Yaml.createGeneratorFactory(Map.of("test", "value"));

        factory.createGenerator(out, UTF_8)
            .writeStartObject()
                .write("name", "test")
            .writeEnd()
            .close();

        assertEquals(Map.of("test", "value"), factory.getConfigInUse());
        assertEquals("name: test\n", out.toString(UTF_8));
    }

    @Test
    void shouldGenerateViaOutputStreamEntryPointsAndFlush()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator generator = Yaml.createGenerator(out);

        generator.writeStartArray()
            .write(1L)
            .write(new BigInteger("2"))
            .write(3.0)
            .writeEnd()
            .flush();

        assertEquals(String.join("\n",
            "- 1",
            "- 2",
            "- 3.0",
            ""), out.toString(UTF_8));
        assertThrows(JsonException.class, () -> generator.write(4));
        generator.close();

        out = new ByteArrayOutputStream();
        Yaml.createGeneratorFactory(Map.of())
            .createGenerator(out)
            .write(JsonValue.TRUE)
            .close();
        assertEquals("true\n", out.toString(UTF_8));
    }

    @Test
    void shouldGenerateEmptyRootValues()
    {
        StringWriter out = new StringWriter();
        Yaml.createGenerator(out).write(JsonValue.EMPTY_JSON_OBJECT).close();
        assertEquals("{}\n", out.toString());

        out = new StringWriter();
        Yaml.createGenerator(out).write(JsonValue.EMPTY_JSON_ARRAY).close();
        assertEquals("[]\n", out.toString());
    }

    @Test
    void shouldRoundTripGeneratedYamlThroughParser()
    {
        StringWriter out = new StringWriter();
        Yaml.createGenerator(out)
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
            "END_OBJECT"), events(Yaml.createParser(new StringReader(out.toString()))));
    }

    @Test
    void shouldRejectIncompleteDocuments()
    {
        JsonGenerator generator = Yaml.createGenerator(new StringWriter());

        generator.writeStartObject().writeKey("name");

        assertThrows(JsonException.class, generator::close);
    }

    @Test
    void shouldRejectInvalidGeneratorState()
    {
        assertThrows(JsonException.class, () -> Yaml.createGenerator(new StringWriter()).writeKey("name"));
        assertThrows(JsonException.class, () -> Yaml.createGenerator(new StringWriter()).writeEnd());
        assertThrows(JsonException.class, () -> Yaml.createGenerator(new StringWriter()).write(Double.NaN));

        JsonGenerator object = Yaml.createGenerator(new StringWriter()).writeStartObject();
        assertThrows(JsonException.class, () -> object.write("value"));
        assertThrows(JsonException.class, () -> object.writeKey("a").writeKey("b"));

        JsonGenerator rooted = Yaml.createGenerator(new StringWriter());
        rooted.write(1);
        assertThrows(JsonException.class, () -> rooted.write(2));

        JsonGenerator generator = Yaml.createGenerator(new StringWriter());
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
