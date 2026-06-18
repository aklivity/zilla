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
package io.aklivity.zilla.runtime.common.json.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

class JsonValuesTest
{
    @Test
    void shouldPreserveBuilderOrdering()
    {
        JsonObject object = JsonValues.objectBuilder()
            .add("b", 2)
            .add("a", 1)
            .add("c", 3)
            .build();
        assertEquals(List.of("b", "a", "c"), List.copyOf(object.keySet()));
    }

    @Test
    void shouldDiffArrayAppendAsAddAtIndex()
    {
        JsonArray source = JsonValues.arrayBuilder().add("a").add("b").build();
        JsonArray target = JsonValues.arrayBuilder().add("a").add("b").add("c").build();
        JsonArray patch = JsonValues.diff(source, target).toJsonArray();
        assertEquals(1, patch.size());
        JsonObject operation = patch.getJsonObject(0);
        assertEquals("add", operation.getString("op"));
        assertEquals("/2", operation.getString("path"));
        assertEquals("c", operation.getString("value"));
    }

    @Test
    void shouldResetObjectBuilderOnBuild()
    {
        JsonObjectBuilder builder = JsonValues.objectBuilder();
        JsonObject first = builder.add("a", 1).build();
        JsonObject second = builder.add("b", 2).build();
        assertEquals(List.of("a"), List.copyOf(first.keySet()));
        assertEquals(List.of("b"), List.copyOf(second.keySet()));
    }

    @Test
    void shouldResetArrayBuilderOnBuild()
    {
        JsonArrayBuilder builder = JsonValues.arrayBuilder();
        JsonArray first = builder.add(1).build();
        JsonArray second = builder.add(2).build();
        assertEquals(1, first.size());
        assertEquals(2, second.getInt(0));
        assertEquals(1, second.size());
    }

    @Test
    void shouldExposeObjectAccessors()
    {
        JsonObject object = JsonValues.objectBuilder()
            .add("name", "test")
            .add("count", 5)
            .add("enabled", true)
            .addNull("absent")
            .add("nested", JsonValues.objectBuilder().add("k", "v"))
            .add("items", JsonValues.arrayBuilder().add("x"))
            .build();
        assertEquals("test", object.getString("name"));
        assertEquals(5, object.getInt("count"));
        assertTrue(object.getBoolean("enabled"));
        assertTrue(object.isNull("absent"));
        assertEquals("v", object.getJsonObject("nested").getString("k"));
        assertEquals("x", object.getJsonArray("items").getString(0));
        assertEquals("v", object.getValue("/nested/k").toString().replace("\"", ""));
        assertEquals(JsonValue.ValueType.OBJECT, object.getValueType());
    }

    @Test
    void shouldExposeArrayAccessors()
    {
        JsonArray array = JsonValues.arrayBuilder()
            .add("one")
            .add(2)
            .add(true)
            .addNull()
            .build();
        assertEquals("one", array.getString(0));
        assertEquals(2, array.getInt(1));
        assertTrue(array.getBoolean(2));
        assertTrue(array.isNull(3));
        assertEquals(JsonValue.ValueType.ARRAY, array.getValueType());
        assertEquals("one", array.getString(0, "fallback"));
        assertEquals("fallback", array.getString(1, "fallback"));
        assertEquals(2, array.getInt(1, 9));
        assertEquals(9, array.getInt(0, 9));
        assertTrue(array.getBoolean(2, false));
        assertFalse(array.getBoolean(0, false));
    }

    @Test
    void shouldCompareValuesStructurally()
    {
        JsonObject left = JsonValues.objectBuilder().add("a", 1).add("b", 2).build();
        JsonObject right = JsonValues.objectBuilder().add("a", 1).add("b", 2).build();
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, JsonValues.objectBuilder().add("a", 1).build());

        assertEquals(JsonValues.string("x"), JsonValues.string("x"));
        assertEquals(JsonValues.number(new BigDecimal("1.0")), JsonValues.number(1));
        assertNotEquals(JsonValues.string("x"), JsonValues.string("y"));
    }

    @Test
    void shouldPreserveLexemeWithoutCanonicalizing()
    {
        assertEquals("100.0", JsonValues.numberLiteral("100.0").toString());
        assertEquals("6.022e23", JsonValues.numberLiteral("6.022e23").toString());
        assertEquals("-0.5", JsonValues.numberLiteral("-0.5").toString());
    }

    @Test
    void shouldDeferDecimalFromLexeme()
    {
        JsonNumber fraction = JsonValues.numberLiteral("1.5");
        assertFalse(fraction.isIntegral());
        assertEquals(new BigDecimal("1.5"), fraction.bigDecimalValue());
        assertEquals(1.5, fraction.doubleValue(), 0.0);

        JsonNumber integral = JsonValues.numberLiteral("42.0");
        assertTrue(integral.isIntegral());
        assertEquals(42, integral.intValueExact());
        assertEquals(1234L, JsonValues.numberLiteral("1.234e3").longValue());
    }

    @Test
    void shouldMatchEagerNumberFromLexeme()
    {
        JsonNumber lexeme = JsonValues.numberLiteral("1.50");
        JsonNumber eager = JsonValues.number(new BigDecimal("1.5"));
        assertEquals(eager, lexeme);
        assertEquals(lexeme, eager);
        assertEquals(eager.hashCode(), lexeme.hashCode());
    }

    @Test
    void shouldConvertNumberForms()
    {
        assertTrue(JsonValues.number(7).isIntegral());
        assertEquals(7L, JsonValues.number(7L).longValue());
        assertEquals(new BigInteger("9"), JsonValues.number(new BigInteger("9")).bigIntegerValue());
        assertEquals(2.5, JsonValues.number(2.5).doubleValue(), 0.0);
        assertEquals(3, ((JsonNumber) JsonValues.value(3)).intValue());
        assertThrows(NumberFormatException.class, () -> JsonValues.number(Double.NaN));
        assertEquals(JsonValue.NULL, JsonValues.value(null));
        assertEquals(JsonValue.TRUE, JsonValues.value(true));
        assertEquals("v", JsonValues.value(Map.of("k", "v")).asJsonObject().getString("k"));
        assertEquals("x", JsonValues.value(List.of("x")).asJsonArray().getString(0));
        assertThrows(JsonException.class, () -> JsonValues.value(new Object()));
    }

    @Test
    void shouldReplayValueAsParser()
    {
        JsonValue value = JsonValues.objectBuilder()
            .add("a", JsonValues.arrayBuilder().add(1).add(JsonValues.objectBuilder().add("deep", true)))
            .add("b", "text")
            .build();

        try (JsonParser parser = JsonValues.parser(value))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            assertEquals(2, parser.getObjectStream().count());
        }

        try (JsonParser parser = JsonValues.parser(value))
        {
            assertEquals(JsonParser.Event.START_OBJECT, parser.next());
            assertEquals(JsonParser.Event.KEY_NAME, parser.next());
            assertEquals("a", parser.getString());
            assertEquals(JsonParser.Event.START_ARRAY, parser.next());
            parser.skipArray();
            assertEquals(JsonParser.Event.KEY_NAME, parser.next());
            assertEquals("b", parser.getString());
            assertEquals(JsonParser.Event.VALUE_STRING, parser.next());
            assertEquals("text", parser.getValue().toString().replace("\"", ""));
        }
    }

    @Test
    void shouldReplayScalarAsParser()
    {
        try (JsonParser parser = JsonValues.parser(JsonValues.number(5)))
        {
            assertEquals(JsonParser.Event.VALUE_NUMBER, parser.next());
            assertTrue(parser.isIntegralNumber());
            assertEquals(5, parser.getInt());
            assertEquals(5L, parser.getLong());
            assertEquals(new BigDecimal("5"), parser.getBigDecimal());

            JsonLocation location = parser.getLocation();
            assertEquals(-1, location.getLineNumber());
            assertEquals(-1, location.getColumnNumber());
            assertEquals(-1, location.getStreamOffset());

            assertFalse(parser.hasNext());
            assertThrows(RuntimeException.class, parser::next);
        }
    }

    @Test
    void shouldDiffAndMergeStructures()
    {
        JsonObject source = JsonValues.objectBuilder().add("a", 1).add("b", 2).build();
        JsonObject target = JsonValues.objectBuilder().add("a", 1).add("c", 3).build();

        JsonObject patched = JsonValues.diff(source, target).apply(source);
        assertEquals(target, patched);

        assertEquals(target, JsonValues.mergeDiff(source, target).apply(source));
        assertEquals(JsonValue.ValueType.OBJECT, JsonValues.mergePatch(target).toJsonValue().getValueType());
    }
}
