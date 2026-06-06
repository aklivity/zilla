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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.junit.jupiter.api.Test;

class YamlProviderTest
{
    @Test
    void shouldCreateYamlParsersReadersGeneratorsAndWriters()
    {
        JsonProvider provider = Yaml.provider();

        JsonParserFactory parserFactory = provider.createParserFactory(Map.of("parser", "value"));
        assertEquals(Map.of("parser", "value"), parserFactory.getConfigInUse());
        try (JsonParser parser = parserFactory.createParser(new StringReader("name: test\n")))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        }

        JsonReaderFactory readerFactory = provider.createReaderFactory(Map.of("reader", "value"));
        assertEquals(Map.of("reader", "value"), readerFactory.getConfigInUse());
        JsonObject object = readerFactory.createReader(new StringReader("name: test\nitems: [1]\n")).readObject();
        assertEquals("test", object.getString("name"));
        assertEquals(1, object.getJsonArray("items").getInt(0));

        JsonArray array = provider.createReader(new ByteArrayInputStream("- true\n- null\n".getBytes(UTF_8))).readArray();
        assertEquals(JsonValue.TRUE, array.get(0));
        assertEquals(JsonValue.NULL, array.get(1));

        JsonValue value = readerFactory.createReader(new ByteArrayInputStream("42\n".getBytes(UTF_8)), UTF_8).readValue();
        assertEquals(JsonValue.ValueType.NUMBER, value.getValueType());

        assertThrows(JsonException.class, () -> provider.createReader(new StringReader("42\n")).readObject());
        JsonReader reader = provider.createReader(new StringReader("name: test\n"));
        reader.readObject();
        assertThrows(JsonException.class, reader::readValue);

        JsonGeneratorFactory generatorFactory = provider.createGeneratorFactory(Map.of("generator", "value"));
        assertEquals(Map.of("generator", "value"), generatorFactory.getConfigInUse());
        StringWriter generated = new StringWriter();
        generatorFactory.createGenerator(generated)
            .writeStartObject()
            .write("name", "test")
            .writeEnd()
            .close();
        assertEquals("name: test\n", generated.toString());

        JsonWriterFactory writerFactory = provider.createWriterFactory(Map.of("writer", "value"));
        assertEquals(Map.of("writer", "value"), writerFactory.getConfigInUse());
        StringWriter writtenObject = new StringWriter();
        writerFactory.createWriter(writtenObject).writeObject(object);
        assertEquals("""
            name: test
            items:
              - 1
            """, writtenObject.toString());

        ByteArrayOutputStream writtenArray = new ByteArrayOutputStream();
        writerFactory.createWriter(writtenArray, UTF_8).writeArray(array);
        assertEquals("""
            - true
            - null
            """, writtenArray.toString(UTF_8));

        ByteArrayOutputStream writtenValue = new ByteArrayOutputStream();
        provider.createWriter(writtenValue).write(JsonValue.TRUE);
        assertEquals("true\n", writtenValue.toString(UTF_8));
    }

    @Test
    void shouldDelegateJsonModelApisToDefaultProvider()
    {
        JsonProvider provider = Yaml.provider();

        JsonObject object = provider.createObjectBuilder()
            .add("name", provider.createValue("test"))
            .add("count", provider.createValue(1))
            .add("long", provider.createValue(2L))
            .add("decimal", provider.createValue(3.0))
            .add("big", provider.createValue(new java.math.BigInteger("4")))
            .add("number", provider.createValue((Number) new java.math.BigDecimal("5.5")))
            .build();
        assertEquals("test", object.getString("name"));

        JsonArray array = provider.createArrayBuilder(List.of("item")).build();
        assertEquals("item", array.getString(0));

        assertEquals("test", provider.createObjectBuilder(object).build().getString("name"));
        assertEquals("mapped", provider.createObjectBuilder(Map.of("key", "mapped")).build().getString("key"));
        assertEquals("item", provider.createArrayBuilder(array).build().getString(0));

        assertEquals("test", provider.createPointer("/name").getValue(object).toString().replace("\"", ""));

        JsonPatch patch = provider.createPatchBuilder()
            .add("/enabled", true)
            .build();
        assertTrue(patch.apply(JsonValue.EMPTY_JSON_OBJECT).asJsonObject().getBoolean("enabled"));

        JsonPatch copied = provider.createPatch(provider.createArrayBuilder()
            .add(provider.createObjectBuilder()
                .add("op", "add")
                .add("path", "/enabled")
                .add("value", true))
            .build());
        assertTrue(copied.apply(JsonValue.EMPTY_JSON_OBJECT).asJsonObject().getBoolean("enabled"));

        assertEquals(object, provider.createMergePatch(object).apply(JsonValue.EMPTY_JSON_OBJECT));
        assertEquals(object, provider.createMergeDiff(JsonValue.EMPTY_JSON_OBJECT, object).apply(JsonValue.EMPTY_JSON_OBJECT));
        assertEquals(JsonValue.ValueType.ARRAY,
            provider.createDiff(JsonValue.EMPTY_JSON_OBJECT, object).toJsonArray().getValueType());
    }
}
