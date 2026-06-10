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

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import jakarta.json.JsonObject;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;

import org.junit.jupiter.api.Test;

class JsonpGeneratorPrettyTest
{
    private static final Map<String, Object> PRETTY = Map.of(JsonGenerator.PRETTY_PRINTING, true);

    @Test
    void shouldPrettyPrintObject()
    {
        JsonProvider provider = JsonProvider.provider();
        JsonGeneratorFactory factory = provider.createGeneratorFactory(PRETTY);

        StringWriter generated = new StringWriter();
        factory.createGenerator(generated)
            .writeStartObject()
            .write("schema", "{\"type\":\"record\"}")
            .write("schemaType", "AVRO")
            .writeEnd()
            .close();

        String expected =
            "{\n" +
            "    \"schema\": \"{\\\"type\\\":\\\"record\\\"}\",\n" +
            "    \"schemaType\": \"AVRO\"\n" +
            "}";
        assertEquals(expected, generated.toString());
    }

    @Test
    void shouldPrettyPrintNestedArray()
    {
        JsonProvider provider = JsonProvider.provider();
        JsonGeneratorFactory factory = provider.createGeneratorFactory(PRETTY);

        StringWriter generated = new StringWriter();
        factory.createGenerator(generated)
            .writeStartObject()
            .write("name", "cities")
            .writeStartArray("fields")
            .writeStartObject()
            .write("name", "id")
            .writeEnd()
            .writeStartObject()
            .write("name", "city")
            .writeEnd()
            .writeEnd()
            .writeEnd()
            .close();

        String expected =
            "{\n" +
            "    \"name\": \"cities\",\n" +
            "    \"fields\": [\n" +
            "        {\n" +
            "            \"name\": \"id\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"name\": \"city\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";
        assertEquals(expected, generated.toString());
    }

    @Test
    void shouldPrettyPrintEmptyObjectAndArray()
    {
        JsonProvider provider = JsonProvider.provider();
        JsonGeneratorFactory factory = provider.createGeneratorFactory(PRETTY);

        StringWriter generated = new StringWriter();
        factory.createGenerator(generated)
            .writeStartObject()
            .writeStartObject("object")
            .writeEnd()
            .writeStartArray("array")
            .writeEnd()
            .writeEnd()
            .close();

        String expected =
            "{\n" +
            "    \"object\": {},\n" +
            "    \"array\": []\n" +
            "}";
        assertEquals(expected, generated.toString());
    }

    @Test
    void shouldPrettyPrintMixedScalars()
    {
        JsonProvider provider = JsonProvider.provider();
        JsonGeneratorFactory factory = provider.createGeneratorFactory(PRETTY);

        ByteArrayOutputStream generated = new ByteArrayOutputStream();
        factory.createGenerator(generated, UTF_8)
            .writeStartArray()
            .write("one")
            .write(2)
            .write(true)
            .writeNull()
            .writeEnd()
            .close();

        String expected =
            "[\n" +
            "    \"one\",\n" +
            "    2,\n" +
            "    true,\n" +
            "    null\n" +
            "]";
        assertEquals(expected, generated.toString(UTF_8));
    }

    @Test
    void shouldPrettyPrintJsonValueDocument()
    {
        JsonProvider provider = JsonProvider.provider();
        String compact = "{\"name\":\"test\",\"items\":[\"one\",2,true,null],\"nested\":{\"enabled\":false}}";
        JsonObject object = provider.createReader(new StringReader(compact)).readObject();

        JsonGeneratorFactory factory = provider.createGeneratorFactory(PRETTY);
        StringWriter generated = new StringWriter();
        JsonGenerator generator = factory.createGenerator(generated);
        generator.write(object);
        generator.close();

        String expected =
            "{\n" +
            "    \"name\": \"test\",\n" +
            "    \"items\": [\n" +
            "        \"one\",\n" +
            "        2,\n" +
            "        true,\n" +
            "        null\n" +
            "    ],\n" +
            "    \"nested\": {\n" +
            "        \"enabled\": false\n" +
            "    }\n" +
            "}";
        assertEquals(expected, generated.toString());
    }

    @Test
    void shouldRemainCompactWithoutPrettyPrinting()
    {
        JsonProvider provider = JsonProvider.provider();

        StringWriter direct = new StringWriter();
        provider.createGenerator(direct)
            .writeStartObject()
            .write("name", "test")
            .writeStartArray("items")
            .write(1)
            .writeEnd()
            .writeEnd()
            .close();
        assertEquals("{\"name\":\"test\",\"items\":[1]}", direct.toString());

        JsonGeneratorFactory factory = provider.createGeneratorFactory(Map.of("generator", "value"));
        StringWriter factoryGenerated = new StringWriter();
        factory.createGenerator(factoryGenerated)
            .writeStartObject()
            .write("name", "test")
            .writeEnd()
            .close();
        assertEquals("{\"name\":\"test\"}", factoryGenerated.toString());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        provider.createGenerator(stream)
            .writeStartArray()
            .write("x")
            .writeEnd()
            .close();
        assertEquals("[\"x\"]", stream.toString(UTF_8));
    }
}
