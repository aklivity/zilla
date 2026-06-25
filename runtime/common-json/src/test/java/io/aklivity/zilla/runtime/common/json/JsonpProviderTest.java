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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.internal.json.JsonProviderImpl;

class JsonpProviderTest
{
    @Test
    void shouldOverrideJsonProviderAbstractMethods()
    {
        JsonProvider provider = JsonProvider.provider();
        assertInstanceOf(JsonProviderImpl.class, provider);
        Class<? extends JsonProvider> providerType = provider.getClass();

        for (Method method : JsonProvider.class.getMethods())
        {
            if (Modifier.isAbstract(method.getModifiers()))
            {
                assertEquals(providerType, override(providerType, method).getDeclaringClass(), method.toString());
            }
        }
    }

    @Test
    void shouldCreateParsersReadersGeneratorsAndWriters()
    {
        JsonProvider provider = JsonProvider.provider();

        JsonParserFactory parserFactory = provider.createParserFactory(Map.of("parser", "value"));
        assertEquals(Map.of("parser", "value"), parserFactory.getConfigInUse());
        try (JsonParser parser = parserFactory.createParser(new StringReader("{\"name\":\"test\"}")))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        }

        JsonReaderFactory readerFactory = provider.createReaderFactory(Map.of("reader", "value"));
        assertEquals(Map.of("reader", "value"), readerFactory.getConfigInUse());
        JsonObject object = readerFactory.createReader(new StringReader("{\"name\":\"test\",\"items\":[1]}")).readObject();
        assertEquals("test", object.getString("name"));
        assertEquals(1, object.getJsonArray("items").getInt(0));

        JsonArray array = provider.createReader(new ByteArrayInputStream("[true,null]".getBytes(UTF_8))).readArray();
        assertEquals(JsonValue.TRUE, array.get(0));
        assertEquals(JsonValue.NULL, array.get(1));

        JsonValue value = readerFactory.createReader(new ByteArrayInputStream("42\n".getBytes(UTF_8)), UTF_8).readValue();
        assertEquals(JsonValue.ValueType.NUMBER, value.getValueType());

        assertThrows(JsonException.class, () -> provider.createReader(new StringReader("42\n")).readObject());
        JsonReader reader = provider.createReader(new StringReader("{\"name\":\"test\"}"));
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
        assertEquals("{\"name\":\"test\"}", generated.toString());

        JsonWriterFactory writerFactory = provider.createWriterFactory(Map.of("writer", "value"));
        assertEquals(Map.of("writer", "value"), writerFactory.getConfigInUse());
        StringWriter writtenObject = new StringWriter();
        writerFactory.createWriter(writtenObject).writeObject(object);
        assertEquals("{\"name\":\"test\",\"items\":[1]}", writtenObject.toString());

        ByteArrayOutputStream writtenArray = new ByteArrayOutputStream();
        writerFactory.createWriter(writtenArray, UTF_8).writeArray(array);
        assertEquals("[true,null]", writtenArray.toString(UTF_8));

        ByteArrayOutputStream writtenValue = new ByteArrayOutputStream();
        provider.createWriter(writtenValue).write(JsonValue.TRUE);
        assertEquals("true", writtenValue.toString(UTF_8));

        JsonObject directObject = Json.createReader(new StringReader("{\"name\":\"direct\"}")).readObject();
        assertEquals("direct", directObject.getString("name"));

        assertEquals(Map.of("reader", "static"), Json.createReaderFactory(Map.of("reader", "static")).getConfigInUse());
        assertEquals(Map.of("writer", "static"), Json.createWriterFactory(Map.of("writer", "static")).getConfigInUse());

        StringWriter directWriter = new StringWriter();
        Json.createWriter(directWriter).writeObject(directObject);
        assertEquals("{\"name\":\"direct\"}", directWriter.toString());

        try (JsonParser parser = Json.createParser(new ByteArrayInputStream("{\"k\":1}".getBytes(UTF_8))))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        }

        StringWriter direct = new StringWriter();
        Json.createGenerator(direct).writeStartArray().write(1).writeEnd().close();
        assertEquals("[1]", direct.toString());

        ByteArrayOutputStream directStream = new ByteArrayOutputStream();
        Json.createGenerator(directStream).write("x").close();
        assertEquals("\"x\"", directStream.toString(UTF_8));
    }

    @Test
    void shouldRoundTripDocument()
    {
        JsonProvider provider = JsonProvider.provider();
        String json = "{\"name\":\"test\",\"items\":[\"one\",2,true,null],\"nested\":{\"enabled\":false}}";

        JsonObject object = provider.createReader(new StringReader(json)).readObject();
        StringWriter writer = new StringWriter();
        provider.createWriter(writer).writeObject(object);
        assertEquals(json, writer.toString());
    }

    @Test
    void shouldBuildAndStreamObjects()
    {
        JsonProvider provider = JsonProvider.provider();

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
            assertEquals(JsonParser.Event.START_OBJECT, parser.currentEvent());
            assertEquals(2, parser.getObjectStream().count());
        }
        try (JsonParser parser = parserFactory.createParser(object.getJsonArray("items")))
        {
            assertEquals(JsonParser.Event.START_ARRAY, parser.next());
            assertEquals(4, parser.getArrayStream().count());
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
        assertEquals("{\"name\":\"test\",\"items\":[\"one\",2,3.5,false,null]," +
            "\"nested\":{\"name\":\"test\",\"items\":[\"one\",2,true,null]}}", generated.toString());
        assertThrows(JsonException.class, () -> generator.write("after"));

        assertThrows(JsonException.class, () -> provider.createGenerator(new StringWriter())
            .writeStartObject()
            .writeKey("name")
            .close());
        assertThrows(JsonException.class, () -> provider.createGenerator(new StringWriter()).write(Double.NaN));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateFactoriesWithDefensiveConfig()
    {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("configured", true);

        JsonParserFactory parserFactory = Json.createParserFactory(mutable);
        JsonReaderFactory readerFactory = Json.createReaderFactory(mutable);
        JsonGeneratorFactory generatorFactory = Json.createGeneratorFactory(mutable);
        JsonWriterFactory writerFactory = Json.createWriterFactory(mutable);

        mutable.put("configured", false);

        assertEquals(true, parserFactory.getConfigInUse().get("configured"));
        assertEquals(true, readerFactory.getConfigInUse().get("configured"));
        assertEquals(true, generatorFactory.getConfigInUse().get("configured"));
        assertEquals(true, writerFactory.getConfigInUse().get("configured"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((Map<String, Object>) generatorFactory.getConfigInUse()).put("changed", true));

        assertTrue(Json.createReaderFactory(null).getConfigInUse().isEmpty());
        assertTrue(Json.createGeneratorFactory(null).getConfigInUse().isEmpty());
        assertTrue(Json.createWriterFactory(null).getConfigInUse().isEmpty());
        assertTrue(Json.createBuilderFactory(null).getConfigInUse().isEmpty());
    }

    @Test
    void shouldProvideJsonModelApisWithoutExternalProvider()
    {
        JsonProvider provider = JsonProvider.provider();
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
        assertEquals("{\"name\":\"test\",\"items\":[\"a\",\"b\"],\"nested\":{\"enabled\":true}," +
            "\"array\":[\"zero\",\"one\",1,false]}", object.toString());
        assertEquals("fallback", object.getString("missing", "fallback"));
        assertEquals(7, object.getInt("missing", 7));
        assertTrue(object.getJsonObject("nested").getBoolean("enabled"));
        assertFalse(object.isNull("missing"));

        JsonNumber decimal = provider.createValue(new BigDecimal("42.0"));
        assertTrue(decimal.isIntegral());
        assertEquals(42, decimal.intValueExact());
        assertEquals(42L, decimal.longValueExact());
        assertEquals(new BigDecimal("42"), decimal.bigDecimalValue());

        JsonObject created = provider.createObjectBuilder()
            .add("name", provider.createValue("test"))
            .add("count", provider.createValue(1))
            .add("long", provider.createValue(2L))
            .add("decimal", provider.createValue(3.0))
            .add("big", provider.createValue(new BigInteger("4")))
            .add("number", provider.createValue((Number) new BigDecimal("5.5")))
            .build();
        assertEquals("test", created.getString("name"));

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

        JsonPatch copied = provider.createPatch(provider.createArrayBuilder()
            .add(provider.createObjectBuilder()
                .add("op", "add")
                .add("path", "/enabled")
                .add("value", true))
            .build());
        assertTrue(copied.apply(JsonValue.EMPTY_JSON_OBJECT).asJsonObject().getBoolean("enabled"));

        JsonMergePatch mergePatch = provider.createMergePatch(provider.createObjectBuilder()
            .addNull("name")
            .add("nested", provider.createObjectBuilder().add("extra", true))
            .build());
        JsonObject merged = mergePatch.apply(object).asJsonObject();
        assertFalse(merged.containsKey("name"));
        assertTrue(merged.getJsonObject("nested").getBoolean("extra"));
        assertEquals(JsonValue.ValueType.OBJECT, mergePatch.toJsonValue().getValueType());

        assertEquals(object, provider.createMergeDiff(JsonValue.EMPTY_JSON_OBJECT, object).apply(JsonValue.EMPTY_JSON_OBJECT));
        assertEquals(JsonValue.ValueType.ARRAY,
            provider.createDiff(JsonValue.EMPTY_JSON_OBJECT, object).toJsonArray().getValueType());

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

        assertEquals(JsonValue.ValueType.OBJECT, provider.createArrayBuilder(List.of(Map.of("k", "v")))
            .build()
            .get(0)
            .getValueType());
        assertEquals("v", provider.createObjectBuilder(Map.of("k", "v")).build().getString("k"));
        assertEquals("test", provider.createObjectBuilder(object).build().getString("name"));
        assertEquals("one", provider.createArrayBuilder(array).build().getString(1));
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
