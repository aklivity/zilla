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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;

import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.spi.YamlProvider;

class YamlTest
{
    @Test
    void shouldCreateNativeProviderWithoutJsonProviderRegistration()
    {
        Object provider = Yaml.provider();

        assertEquals("YamlProviderImpl", provider.getClass().getSimpleName());
        assertFalse(provider instanceof JsonProvider);
        assertNotSame(provider, YamlJson.provider());
        assertNotSame(YamlJson.provider(), JsonProvider.provider());
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
        assertEquals("name", object.iterator().next().name());
        assertEquals("name", object.entries().get(0).key().asYamlScalar().getString());
        assertEquals("test", object.get("name").asYamlScalar().getString());
        assertNull(object.get("unknown"));

        YamlArray array = object.get("items").asYamlArray();
        assertSame(array, array.asYamlArray());
        assertEquals(YamlValue.ValueType.ARRAY, array.getValueType());
        assertEquals(2, array.size());
        assertEquals("one", array.iterator().next().asYamlScalar().getString());
        assertEquals(YamlScalarType.NUMBER, array.get(1).asYamlScalar().getScalarType());
        assertEquals("2", array.get(1).asYamlScalar().getString());

        YamlScalar enabled = object.get("enabled").asYamlScalar();
        assertSame(enabled, enabled.asYamlScalar());
        assertEquals(YamlValue.ValueType.TRUE, enabled.getValueType());
        assertEquals(YamlScalarType.BOOLEAN, enabled.getScalarType());
        assertEquals("true", enabled.getString());

        YamlScalar missing = object.get("missing").asYamlScalar();
        assertEquals(YamlValue.ValueType.NULL, missing.getValueType());
        assertEquals(YamlScalarType.NULL, missing.getScalarType());
        assertNull(missing.getString());
    }

    @Test
    void shouldReadNativeArrayAndScalarValues()
    {
        YamlArray array = Yaml.createReader(new ByteArrayInputStream("- false\n- null\n".getBytes(UTF_8))).readArray();
        assertEquals(YamlValue.ValueType.FALSE, array.get(0).getValueType());
        assertEquals(YamlValue.ValueType.NULL, array.get(1).getValueType());

        YamlValue scalar = Yaml.createParser(new ByteArrayInputStream("42\n".getBytes(UTF_8))).parse();
        assertEquals(YamlValue.ValueType.NUMBER, scalar.getValueType());
        assertEquals(YamlScalarType.NUMBER, scalar.asYamlScalar().getScalarType());
        assertEquals("42", scalar.asYamlScalar().getString());
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
    }

    @Test
    void shouldWriteNativeValues()
    {
        YamlObject object = Yaml.createReader(new StringReader("name: test\n")).readObject();
        StringWriter objectWriter = new StringWriter();
        ByteArrayOutputStream arrayWriter = new ByteArrayOutputStream();

        Yaml.createWriter(objectWriter).writeObject(object);
        Yaml.provider().createWriter(arrayWriter).writeArray(
            Yaml.createReader(new StringReader("- one\n")).readArray());

        assertEquals("name: test\n", objectWriter.toString());
        assertEquals("- one\n", arrayWriter.toString(UTF_8));
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
    }
}
