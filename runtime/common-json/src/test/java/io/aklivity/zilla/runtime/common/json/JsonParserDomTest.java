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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

class JsonParserDomTest
{
    @Test
    void shouldBuildNestedObject()
    {
        JsonParser parser = parserFor("{\"a\":1,\"b\":{\"c\":[true,null,\"x\"]},\"d\":-2.5e3}");

        assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        JsonObject object = parser.getObject();
        assertEquals(1, object.getInt("a"));
        JsonArray inner = object.getJsonObject("b").getJsonArray("c");
        assertEquals(JsonValue.TRUE, inner.get(0));
        assertEquals(JsonValue.NULL, inner.get(1));
        assertEquals("x", inner.getString(2));
        assertEquals(0, new BigDecimal("-2.5e3").compareTo(object.getJsonNumber("d").bigDecimalValue()));
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldBuildArrayOfObjects()
    {
        JsonParser parser = parserFor("[{\"k\":1},{\"k\":2}]");

        assertEquals(JsonParser.Event.START_ARRAY, parser.next());
        JsonArray array = parser.getArray();
        assertEquals(2, array.size());
        assertEquals(1, array.getJsonObject(0).getInt("k"));
        assertEquals(2, array.getJsonObject(1).getInt("k"));
    }

    @Test
    void shouldGetValueForEachScalar()
    {
        assertEquals(JsonValue.ValueType.STRING, valueOf("\"text\"").getValueType());
        assertEquals(JsonValue.ValueType.NUMBER, valueOf("12\n").getValueType());
        assertEquals(JsonValue.TRUE, valueOf("true"));
        assertEquals(JsonValue.FALSE, valueOf("false"));
        assertEquals(JsonValue.NULL, valueOf("null"));
        assertEquals(JsonValue.ValueType.OBJECT, valueOf("{}").getValueType());
        assertEquals(JsonValue.ValueType.ARRAY, valueOf("[]").getValueType());
    }

    @Test
    void shouldStreamObjectEntriesInOrder()
    {
        JsonParser parser = parserFor("{\"a\":1,\"b\":2,\"c\":3}");

        assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        String keys = parser.getObjectStream()
            .map(Map.Entry::getKey)
            .collect(Collectors.joining(","));
        assertEquals("a,b,c", keys);
    }

    @Test
    void shouldStreamArrayValues()
    {
        JsonParser parser = parserFor("[10,20,30]");

        assertEquals(JsonParser.Event.START_ARRAY, parser.next());
        int sum = parser.getArrayStream()
            .mapToInt(v -> ((JsonNumber) v).intValue())
            .sum();
        assertEquals(60, sum);
    }

    @Test
    void shouldStreamSingleTopLevelValue()
    {
        JsonParser parser = parserFor("{\"only\":true}");

        long count = parser.getValueStream().count();
        assertEquals(1, count);
    }

    @Test
    void shouldThrowGettingValueWithoutPosition()
    {
        JsonParser parser = parserFor("1");

        assertThrows(IllegalStateException.class, parser::getValue);
    }

    @Test
    void shouldGetObjectViaGetValueDispatch()
    {
        JsonParser parser = parserFor("{\"nested\":{\"x\":1}}");

        assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        assertEquals(JsonParser.Event.KEY_NAME, parser.next());
        assertEquals("nested", parser.getString());
        assertEquals(JsonParser.Event.START_OBJECT, parser.next());
        JsonValue value = parser.getValue();
        assertTrue(value instanceof JsonObject);
        assertEquals(1, value.asJsonObject().getInt("x"));
    }

    private static JsonValue valueOf(
        String json)
    {
        JsonParser parser = parserFor(json);
        parser.next();
        return parser.getValue();
    }

    private static JsonParser parserFor(
        String json)
    {
        return JsonEx.createParser(new ByteArrayInputStream(json.getBytes(UTF_8)));
    }
}
