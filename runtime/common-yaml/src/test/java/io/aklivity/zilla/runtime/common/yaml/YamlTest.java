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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

class YamlTest
{
    @Test
    void shouldCreateNativeProviderWithJsonProviderRegistration()
    {
        Object provider = Yaml.provider();

        assertEquals("YamlProviderImpl", provider.getClass().getSimpleName());
        assertFalse(provider instanceof JsonProvider);
        assertNotSame(provider, YamlJson.provider());
        assertEquals("YamlJsonProvider", JsonProvider.provider().getClass().getSimpleName());
    }

    @Test
    void shouldCreateNativeFactoryMethods()
    {
        assertNotNull(Yaml.provider());
        assertNotNull(Yaml.createParser(new StringReader("name: test\n")));
        assertNotNull(Yaml.createParser(new ByteArrayInputStream("name: test\n".getBytes(UTF_8))));
        assertNotNull(Yaml.createReader(new StringReader("name: test\n")));
        assertNotNull(Yaml.createReader(new ByteArrayInputStream("name: test\n".getBytes(UTF_8))));
        assertNotNull(Yaml.createGenerator(new StringWriter()));
        assertNotNull(Yaml.createGenerator(new ByteArrayOutputStream()));
        assertNotNull(Yaml.createWriter(new StringWriter()));
        assertNotNull(Yaml.createWriter(new ByteArrayOutputStream()));
    }

    @Test
    void shouldReadNativeObjectValues()
    {
        YamlProvider provider = Yaml.provider();

        YamlObject object = provider.createReader(new StringReader("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """)).readObject();

        assertEquals(YamlValue.ValueType.OBJECT, object.getValueType());
        assertSame(object, object.asYamlObject());
        assertTrue(object.containsKey("items"));
        assertEquals(4, object.size());
        assertEquals("test", object.getString("name"));
        assertEquals("fallback", object.getString("unknown", "fallback"));

        YamlArray array = object.getArray("items");
        assertSame(array, array.asYamlArray());
        assertEquals(YamlValue.ValueType.ARRAY, array.getValueType());
        assertEquals(2, array.size());
        assertEquals("one", array.getString(0));
        assertEquals(YamlScalarType.NUMBER, array.getScalar(1).getType());
        assertEquals("2", array.getScalar(1).getString());
        assertEquals(2, array.getInt(1));

        YamlScalar enabled = object.getScalar("enabled");
        assertSame(enabled, enabled.asYamlScalar());
        assertEquals(YamlValue.ValueType.TRUE, enabled.getValueType());
        assertEquals(YamlScalarType.BOOLEAN, enabled.getType());
        assertEquals("true", enabled.getString());
        assertTrue(object.getBoolean("enabled"));

        YamlScalar missing = object.getScalar("missing");
        assertEquals(YamlValue.ValueType.NULL, missing.getValueType());
        assertEquals(YamlScalarType.NULL, missing.getType());
        assertNull(missing.getString());
        assertTrue(object.isNull("missing"));
    }

    @Test
    void shouldReadNativeArrayAndScalarValues()
    {
        YamlArray array = Yaml.createReader(new ByteArrayInputStream("- false\n- null\n".getBytes(UTF_8))).readArray();
        assertEquals(YamlValue.ValueType.FALSE, array.get(0).getValueType());
        assertEquals(YamlValue.ValueType.NULL, array.get(1).getValueType());
        assertFalse(array.getBoolean(0));
        assertTrue(array.isNull(1));

        YamlValue scalar = Yaml.createParser(new ByteArrayInputStream("42\n".getBytes(UTF_8))).parse();
        assertEquals(YamlValue.ValueType.NUMBER, scalar.getValueType());
        assertEquals(YamlScalarType.NUMBER, scalar.asYamlScalar().getType());
        assertEquals("42", scalar.asYamlScalar().getString());
    }

    @Test
    void shouldParseNativeEvents()
    {
        YamlParser parser = Yaml.createParser(new StringReader("""
            name: test
            items:
              - 1
              - false
            """));
        List<String> events = new ArrayList<>();

        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            events.add(event.getEventType() + (event.getString() != null ? ":" + event.getString() : ""));
            assertSame(event.getValue(), parser.getValue());
            assertEquals(event.getString(), parser.getString());
            if (event.getValue() != null && event.getEventType() == YamlEvent.EventType.START_OBJECT)
            {
                assertEquals(YamlValue.ValueType.OBJECT, parser.getObject().getValueType());
            }
            if (event.getValue() != null && event.getEventType() == YamlEvent.EventType.START_ARRAY)
            {
                assertEquals(YamlValue.ValueType.ARRAY, parser.getArray().getValueType());
            }
            if (event.getEventType() == YamlEvent.EventType.VALUE_NUMBER)
            {
                assertEquals(YamlScalarType.NUMBER, parser.getScalar().getType());
            }
        }

        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:items",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "VALUE_FALSE",
            "END_ARRAY",
            "END_OBJECT"), events);
        assertFalse(parser.hasNext());
        assertThrows(IllegalStateException.class, parser::next);
        assertThrows(IllegalStateException.class, parser::parse);
    }

    @Test
    void shouldGenerateNativeValues()
    {
        YamlObject object = Yaml.createReader(new StringReader("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """)).readObject();
        StringWriter generated = new StringWriter();

        Yaml.createGenerator(generated).write(object).close();

        assertEquals("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """, generated.toString());

        StringWriter streamed = new StringWriter();
        Yaml.createGenerator(streamed)
            .writeStartObject()
            .write("name", "test")
            .writeStartArray("items")
            .write("one")
            .write(2)
            .writeEnd()
            .write("enabled", true)
            .writeNull("missing")
            .writeEnd()
            .close();

        assertEquals("""
            name: test
            items:
              - one
              - 2
            enabled: true
            missing: null
            """, streamed.toString());
    }

    @Test
    void shouldRoundTripNativeSource()
    {
        String yaml = """
            %YAML 1.2
            ---
            # leading comment
            defaults: &defaults
              name: !!str test
              description: |-
                line one
                line two
            flow: [1, "two", {enabled: true}]
            item:
              <<: *defaults
              description: >-
                folded
                text
            ...
            """;
        YamlValue value = Yaml.createReader(new StringReader(yaml)).readValue();
        StringWriter generated = new StringWriter();

        Yaml.createWriter(generated).write(value);

        assertEquals(yaml, generated.toString());
    }

    @Test
    void shouldWriteNativeValues()
    {
        YamlObject object = Yaml.createReader(new StringReader("name: test\n")).readObject();
        StringWriter objectWriter = new StringWriter();
        ByteArrayOutputStream arrayWriter = new ByteArrayOutputStream();
        StringWriter stringWriter = new StringWriter();
        StringWriter numberWriter = new StringWriter();
        StringWriter booleanWriter = new StringWriter();
        StringWriter nullWriter = new StringWriter();

        Yaml.createWriter(objectWriter).writeObject(object);
        Yaml.provider().createWriter(arrayWriter).writeArray(
            Yaml.createReader(new StringReader("- one\n")).readArray());
        Yaml.createWriter(stringWriter).writeString("test");
        Yaml.createWriter(numberWriter).writeNumber(42);
        Yaml.createWriter(booleanWriter).writeBoolean(true);
        Yaml.createWriter(nullWriter).writeNull();

        assertEquals("name: test\n", objectWriter.toString());
        assertEquals("- one\n", arrayWriter.toString(UTF_8));
        assertEquals("test\n", stringWriter.toString());
        assertEquals("42\n", numberWriter.toString());
        assertEquals("true\n", booleanWriter.toString());
        assertEquals("null\n", nullWriter.toString());
    }

    @Test
    void shouldRejectInvalidNativeUsage()
    {
        YamlParser parser = Yaml.createParser(new StringReader("name: test\n"));
        parser.parse();

        assertThrows(IllegalStateException.class, parser::parse);
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).read());
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).readObject());
        assertThrows(IllegalStateException.class, () -> Yaml.createReader(new StringReader("42\n")).readArray());

        StringWriter writer = new StringWriter();
        YamlGenerator generator = Yaml.createGenerator(writer);
        generator.write(Yaml.createReader(new StringReader("name: test\n")).readObject());
        assertThrows(IllegalStateException.class,
            () -> generator.write(Yaml.createReader(new StringReader("name: other\n")).readObject()));
        generator.close();
        assertThrows(IllegalStateException.class,
            () -> generator.write(Yaml.createReader(new StringReader("name: closed\n")).readObject()));

        assertThrows(IllegalStateException.class, () -> Yaml.createGenerator(new StringWriter()).writeKey("name"));
        assertThrows(IllegalStateException.class, () -> Yaml.createGenerator(new StringWriter()).writeEnd());
        assertThrows(IllegalArgumentException.class, () -> Yaml.createGenerator(new StringWriter()).write(Double.NaN));

        YamlGenerator open = Yaml.createGenerator(new StringWriter()).writeStartObject().writeKey("name");
        assertThrows(IllegalStateException.class, open::writeEnd);

        YamlGenerator incomplete = Yaml.createGenerator(new StringWriter()).writeStartArray();
        assertThrows(IllegalStateException.class, incomplete::close);
    }
}
