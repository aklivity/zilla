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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonException;
import jakarta.json.JsonMergePatch;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatch;
import jakarta.json.JsonPointer;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

class YamlJsonProviderTest
{
    @Test
    void shouldOverrideJsonProviderAbstractMethods()
    {
        Class<? extends JsonProvider> providerType = YamlJson.provider().getClass();

        for (Method method : JsonProvider.class.getMethods())
        {
            if (Modifier.isAbstract(method.getModifiers()))
            {
                assertEquals(providerType, override(providerType, method).getDeclaringClass(), method.toString());
            }
        }
    }

    @Test
    void shouldCreateYamlParsersReadersGeneratorsAndWriters()
    {
        JsonProvider provider = YamlJson.provider();

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

        JsonObject globalObject = Json.createReader(new StringReader("name: global\n")).readObject();
        assertEquals("global", globalObject.getString("name"));

        JsonObject jsonObject = readerFactory.createReader(new StringReader("""
            {
              "name": "test",
              "items": [1]
            }
            """)).readObject();
        assertEquals("test", jsonObject.getString("name"));
        assertEquals(1, jsonObject.getJsonArray("items").getInt(0));

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

        JsonObject directObject = YamlJson.createReader(new StringReader("name: direct\n")).readObject();
        assertEquals("direct", directObject.getString("name"));

        assertEquals(Map.of("reader", "static"), YamlJson.createReaderFactory(Map.of("reader", "static"))
            .getConfigInUse());
        assertEquals(Map.of("writer", "static"), YamlJson.createWriterFactory(Map.of("writer", "static"))
            .getConfigInUse());

        StringWriter directWriter = new StringWriter();
        YamlJson.createWriter(directWriter).writeObject(directObject);
        assertEquals("name: direct\n", directWriter.toString());

        JsonObject staticObject = Json.createObjectBuilder()
            .add("name", "static")
            .add("values", Json.createArrayBuilder().add(1).add(2))
            .build();
        assertEquals("static", staticObject.getString("name"));
        assertEquals(2, staticObject.getJsonArray("values").getInt(1));
        assertEquals("static", Json.createPointer("/name").getValue(staticObject).toString().replace("\"", ""));
    }

    @Test
    void shouldBehaveAsJsonProviderDropIn()
    {
        JsonProvider provider = YamlJson.provider();

        JsonObject object = provider.createObjectBuilder()
            .add("name", "test")
            .add("items", provider.createArrayBuilder()
                .add("one")
                .add(2)
                .add(true)
                .addNull())
            .build();

        JsonParserFactory parserFactory = provider.createParserFactory(Map.of());
        try (JsonParser parser = parserFactory.createParser(object))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            assertEquals(JsonParser.Event.KEY_NAME, parser.next());
            assertEquals("name", parser.getString());
            assertEquals(JsonParser.Event.VALUE_STRING, parser.next());
            assertEquals("test", parser.getString());
        }
        try (JsonParser parser = parserFactory.createParser(object.getJsonArray("items")))
        {
            assertEquals(JsonParser.Event.START_ARRAY, parser.next());
            assertEquals(JsonParser.Event.VALUE_STRING, parser.next());
            assertEquals("one", parser.getString());
        }

        StringWriter generated = new StringWriter();
        JsonGenerator generator = provider.createGenerator(generated)
            .writeStartObject()
            .write("name", "test")
            .writeStartArray("items")
            .write("one")
            .write(new BigInteger("2"))
            .write(new BigDecimal("3.5"))
            .write(false)
            .writeNull()
            .writeEnd()
            .write("nested", object)
            .writeEnd();
        generator.flush();
        generator.close();
        assertEquals("""
            name: test
            items:
              - one
              - 2
              - 3.5
              - false
              - null
            nested:
              name: test
              items:
                - one
                - 2
                - true
                - null
            """, generated.toString());
        assertThrows(JsonException.class, () -> generator.write("after"));

        assertThrows(JsonException.class, () -> provider.createGenerator(new StringWriter())
            .writeStartObject()
            .writeKey("name")
            .close());
        assertThrows(JsonException.class, () -> provider.createGenerator(new StringWriter()).write(Double.NaN));

        JsonObjectBuilder builder = provider.createObjectBuilder()
            .add("zero", 0)
            .add("decimal", new BigDecimal("1.25"))
            .add("big", new BigInteger("42"))
            .add("long", 43L)
            .add("double", 4.5)
            .add("enabled", true)
            .addNull("missing")
            .add("nested", provider.createObjectBuilder().add("name", "child"))
            .add("array", provider.createArrayBuilder().add("item"));
        assertEquals(
            "{\"zero\":0,\"decimal\":1.25,\"big\":42,\"long\":43,\"double\":4.5,\"enabled\":true," +
            "\"missing\":null,\"nested\":{\"name\":\"child\"},\"array\":[\"item\"]}",
            builder.build().toString());

        assertThrows(JsonParsingException.class, () -> provider.createParser(new StringReader("broken: [1\n")));
        assertThrows(JsonException.class, () -> provider.createReader(new StringReader("[]\n")).readObject());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateYamlJsonFactoriesWithDefensiveConfig()
    {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("configured", true);

        JsonParserFactory parserFactory = YamlJson.createParserFactory(mutable);
        JsonReaderFactory readerFactory = YamlJson.createReaderFactory(mutable);
        JsonGeneratorFactory generatorFactory = YamlJson.createGeneratorFactory(mutable);
        JsonWriterFactory writerFactory = YamlJson.createWriterFactory(mutable);

        mutable.put("configured", false);

        assertEquals(true, parserFactory.getConfigInUse().get("configured"));
        assertEquals(true, readerFactory.getConfigInUse().get("configured"));
        assertEquals(true, generatorFactory.getConfigInUse().get("configured"));
        assertEquals(true, writerFactory.getConfigInUse().get("configured"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) parserFactory.getConfigInUse()).put("changed", true));

        assertTrue(YamlJson.createParserFactory(null).getConfigInUse().isEmpty());
        assertTrue(YamlJson.createReaderFactory(null).getConfigInUse().isEmpty());
        assertTrue(YamlJson.createGeneratorFactory(null).getConfigInUse().isEmpty());
        assertTrue(YamlJson.createWriterFactory(null).getConfigInUse().isEmpty());
    }

    @Test
    void shouldDelegateJsonModelApisToDefaultProvider()
    {
        JsonProvider provider = YamlJson.provider();

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

    @Test
    void shouldProvideJsonModelApisWithoutExternalProvider()
    {
        JsonProvider provider = YamlJson.provider();
        JsonBuilderFactory builderFactory = provider.createBuilderFactory(Map.of("builder", "value"));
        assertEquals(Map.of("builder", "value"), builderFactory.getConfigInUse());

        JsonArrayBuilder arrayBuilder = builderFactory.createArrayBuilder()
            .add("zero")
            .add(2)
            .add(false)
            .addNull();
        arrayBuilder.add(1, "one");
        arrayBuilder.set(2, 1);
        arrayBuilder.remove(4);
        JsonArray array = arrayBuilder.build();
        assertEquals("one", array.getString(1));
        assertEquals(1, array.getInt(2));
        assertFalse(array.getBoolean(3));
        assertEquals(4, array.getValuesAs(JsonValue.class).size());

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("name", "test");
        mapped.put("count", 1);
        mapped.put("items", List.of("a", "b"));
        JsonObject object = builderFactory.createObjectBuilder(mapped)
            .add("nested", builderFactory.createObjectBuilder().add("enabled", true))
            .add("array", builderFactory.createArrayBuilder(array))
            .remove("count")
            .build();
        assertEquals("""
            {"name":"test","items":["a","b"],"nested":{"enabled":true},"array":["zero","one",1,false]}
            """.strip(), object.toString());
        assertEquals("fallback", object.getString("missing", "fallback"));
        assertEquals(7, object.getInt("missing", 7));
        assertTrue(object.getJsonObject("nested").getBoolean("enabled"));
        assertFalse(object.isNull("missing"));

        JsonNumber decimal = provider.createValue(new BigDecimal("42.0"));
        assertTrue(decimal.isIntegral());
        assertEquals(42, decimal.intValueExact());
        assertEquals(42L, decimal.longValueExact());
        assertEquals(new BigDecimal("42"), decimal.bigDecimalValue());

        try (JsonParser parser = provider.createParserFactory(Map.of()).createParser(object))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            assertEquals(JsonParser.Event.START_OBJECT, parser.currentEvent());
            assertEquals(4, parser.getObjectStream().count());
        }
        try (JsonParser parser = provider.createParserFactory(Map.of()).createParser(array))
        {
            assertEquals(JsonParser.Event.START_ARRAY, parser.next());
            assertEquals(4, parser.getArrayStream().count());
        }
        try (JsonParser parser = provider.createParserFactory(Map.of()).createParser(provider.createObjectBuilder()
            .add("skip", provider.createObjectBuilder()
                .add("nested", provider.createArrayBuilder()
                    .add(1)
                    .add(provider.createObjectBuilder().add("deep", true))))
            .add("after", "done")
            .build()))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            assertEquals(JsonParser.Event.KEY_NAME, parser.next());
            assertEquals("skip", parser.getString());
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            parser.skipObject();
            assertEquals(JsonParser.Event.END_OBJECT, parser.currentEvent());
            assertEquals(JsonParser.Event.KEY_NAME, parser.next());
            assertEquals("after", parser.getString());
        }

        JsonPointer nested = provider.createPointer("/nested/enabled");
        assertTrue(nested.containsValue(object));
        assertEquals(JsonValue.TRUE, nested.getValue(object));
        assertFalse(provider.createPointer("/nested/missing").containsValue(object));
        assertTrue(provider.createPointer("/added").add(object, JsonValue.TRUE).getBoolean("added"));
        assertFalse(provider.createPointer("/nested/enabled").replace(object, JsonValue.FALSE)
            .getJsonObject("nested")
            .getBoolean("enabled"));
        assertFalse(provider.createPointer("/nested/enabled").remove(object).getJsonObject("nested").containsKey("enabled"));

        JsonPatch patch = provider.createPatchBuilder()
            .add("/copied", "value")
            .copy("/moved", "/copied")
            .move("/renamed", "/moved")
            .replace("/name", "patched")
            .test("/name", "patched")
            .remove("/copied")
            .build();
        JsonObject patched = patch.apply(object);
        assertEquals("patched", patched.getString("name"));
        assertEquals("value", patched.getString("renamed"));
        assertFalse(patched.containsKey("copied"));
        assertEquals(JsonValue.ValueType.ARRAY, patch.toJsonArray().getValueType());

        JsonMergePatch mergePatch = provider.createMergePatch(provider.createObjectBuilder()
            .addNull("name")
            .add("nested", provider.createObjectBuilder().add("extra", true))
            .build());
        JsonObject merged = mergePatch.apply(object).asJsonObject();
        assertFalse(merged.containsKey("name"));
        assertTrue(merged.getJsonObject("nested").getBoolean("extra"));
        assertEquals(JsonValue.ValueType.OBJECT, mergePatch.toJsonValue().getValueType());

        JsonArray overloads = provider.createArrayBuilder()
            .add(0, new BigDecimal("1.5"))
            .add(1, new BigInteger("2"))
            .add(2, 3L)
            .add(3, 4.5)
            .add(4, true)
            .addNull(5)
            .add(6, provider.createObjectBuilder().add("name", "child"))
            .add(7, provider.createArrayBuilder().add("nested"))
            .set(0, "one")
            .set(1, new BigDecimal("2.5"))
            .set(2, new BigInteger("3"))
            .set(3, 4L)
            .set(4, 5.5)
            .set(5, false)
            .setNull(6)
            .set(7, provider.createObjectBuilder().add("name", "updated"))
            .build();
        assertEquals("one", overloads.getString(0));
        assertEquals(new BigDecimal("2.5"), overloads.getJsonNumber(1).bigDecimalValue());
        assertFalse(overloads.getBoolean(5));
        assertTrue(overloads.isNull(6));
        assertEquals("updated", overloads.getJsonObject(7).getString("name"));

        JsonArray pointerArray = provider.createPointer("/-").add(provider.createArrayBuilder().add("first").build(),
            provider.createValue("last"));
        pointerArray = provider.createPointer("/0").replace(pointerArray, provider.createValue("updated"));
        pointerArray = provider.createPointer("/1").remove(pointerArray);
        assertEquals(1, pointerArray.size());
        assertEquals("updated", pointerArray.getString(0));

        assertEquals("scalar", provider.createMergePatch(provider.createValue("scalar"))
            .apply(JsonValue.EMPTY_JSON_OBJECT)
            .toString()
            .replace("\"", ""));
    }

    private static Method override(
        Class<?> type,
        Method method)
    {
        try
        {
            return type.getMethod(method.getName(), method.getParameterTypes());
        }
        catch (NoSuchMethodException ex)
        {
            throw new AssertionError(ex);
        }
    }
}
