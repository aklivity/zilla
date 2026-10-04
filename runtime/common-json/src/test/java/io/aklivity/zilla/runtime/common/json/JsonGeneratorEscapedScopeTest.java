/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.util.Map;

import jakarta.json.JsonException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonGeneratorEscapedScopeTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    void shouldEscapeObjectAsStringValue()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject();
        generator.writeKey("a");
        generator.writeStartEscaped();
        generator.writeStartObject();
        generator.writeKey("b");
        generator.write(1);
        generator.writeEnd();
        generator.writeEndEscaped();
        generator.writeEnd();

        assertEquals("{\"a\":\"{\\\"b\\\":1}\"}", output(generator));
    }

    @Test
    void shouldEscapeArrayAndScalarsAsStringValues()
    {
        final JsonGeneratorEx array = generator();
        array.writeStartObject().writeKey("a").writeStartEscaped();
        array.writeStartArray().write(1).write("x").write(true).writeNull().writeEnd();
        array.writeEndEscaped().writeEnd();
        assertEquals("{\"a\":\"[1,\\\"x\\\",true,null]\"}", output(array));

        final JsonGeneratorEx number = generator();
        number.writeStartObject().writeKey("a").writeStartEscaped().write(42).writeEndEscaped().writeEnd();
        assertEquals("{\"a\":\"42\"}", output(number));

        final JsonGeneratorEx string = generator();
        string.writeStartObject().writeKey("a").writeStartEscaped().write("x").writeEndEscaped().writeEnd();
        assertEquals("{\"a\":\"\\\"x\\\"\"}", output(string));
    }

    @Test
    void shouldEscapeEmptyScopeAsEmptyString()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped().writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"\"}", output(generator));
    }

    @Test
    void shouldEscapeArrayElementsAndTopLevelValue()
    {
        final JsonGeneratorEx elements = generator();
        elements.writeStartArray().write(1);
        elements.writeStartEscaped().writeStartObject().writeEnd().writeEndEscaped();
        elements.writeStartEscaped().write(2).writeEndEscaped();
        elements.write(3).writeEnd();
        assertEquals("[1,\"{}\",\"2\",3]", output(elements));

        final JsonGeneratorEx top = generator();
        top.writeStartEscaped().writeStartObject().writeKey("b").write(1).writeEnd().writeEndEscaped();
        assertEquals("\"{\\\"b\\\":1}\"", output(top));
    }

    @Test
    void shouldEscapeNestedScopesTwice()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeStartObject().writeKey("c").writeStartEscaped();
        generator.writeStartObject().writeKey("d").write(1).writeEnd();
        generator.writeEndEscaped();
        generator.writeEnd();
        generator.writeEndEscaped();
        generator.writeEnd();

        assertEquals("{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"d\\\\\\\":1}\\\"}\"}", output(generator));
    }

    @Test
    void shouldEscapeContentOfStringsInsideScopes()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeStartObject().writeKey("b").write("q\"\\\n\u0001é").writeEnd();
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\"a\":\"{\\\"b\\\":\\\"q\\\\\\\"\\\\\\\\\\\\n\\\\u0001é\\\"}\"}", output(generator));
    }

    @Test
    void shouldContinueUnescapedAfterScope()
    {
        final JsonGeneratorEx generator = generator();

        generator.writeStartObject().writeKey("a").writeStartEscaped().write("x").writeEndEscaped();
        generator.writeKey("b").write("y\"z").writeEnd();

        assertEquals("{\"a\":\"\\\"x\\\"\",\"b\":\"y\\\"z\"}", output(generator));
    }

    @Test
    void shouldEscapeScopeInsideConfiguredEscape()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        generator.wrap(buffer, 0, buffer.capacity());

        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.writeStartObject().writeKey("b").write(1).writeEnd();
        generator.writeEndEscaped().writeEnd();

        assertEquals("{\\\"a\\\":\\\"{\\\\\\\"b\\\\\\\":1}\\\"}", output(generator));
    }

    @Test
    void shouldReturnToConfiguredEscapeOnReset()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        generator.wrap(buffer, 0, buffer.capacity());
        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.reset();
        generator.wrap(buffer, 0, buffer.capacity());

        generator.writeStartObject().writeKey("a").write(1).writeEnd();

        assertEquals("{\\\"a\\\":1}", output(generator));
    }

    @Test
    void shouldReturnToPlainOutputOnReset()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.reset();
        generator.wrap(buffer, 0, buffer.capacity());

        generator.writeStartObject().writeKey("a").write("x").writeEnd();

        assertEquals("{\"a\":\"x\"}", output(generator));
    }

    @Test
    void shouldRejectEndWithoutStart()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").write(1);

        assertThrows(JsonException.class, generator::writeEndEscaped);
    }

    @Test
    void shouldRejectStartInKeyPosition()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject();

        assertThrows(JsonException.class, generator::writeStartEscaped);
    }

    @Test
    void shouldRejectStartInKeyPositionAfterMember()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").write(1);

        assertThrows(JsonException.class, generator::writeStartEscaped);
    }

    @Test
    void shouldRejectStartInsideString()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a");
        generator.write("partial", JsonGeneratorEx.Completion.INCOMPLETE);

        assertThrows(JsonException.class, generator::writeStartEscaped);
    }

    @Test
    void shouldRejectEndWhileContainerOpenInsideScope()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").writeStartEscaped().writeStartObject();

        assertThrows(JsonException.class, generator::writeEndEscaped);
    }

    @Test
    void shouldRejectEndWhileKeyAwaitsValueInsideScope()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").writeStartEscaped().writeStartObject().writeKey("b");

        assertThrows(JsonException.class, generator::writeEndEscaped);
    }

    @Test
    void shouldNotWriteStartEscapedWithoutRoom()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(buffer, 0, 8);
        generator.writeStartObject().writeKey("a");
        assertEquals(5, generator.length());

        assertTrue(generator.writeStartEscapedEx());
        assertEquals(6, generator.length());
        assertTrue(generator.writeStartObjectEx());
        assertEquals(7, generator.length());
        assertTrue(generator.writeEndEx());
        assertEquals(8, generator.length());
        assertFalse(generator.writeEndEscapedEx());
        assertEquals(8, generator.length());

        generator.wrap(buffer, 0, 8);
        assertTrue(generator.writeEndEscapedEx());
        assertEquals(1, generator.length());
    }

    @Test
    void shouldNotWriteStartEscapedWithoutRoomForSeparatorAndNestedQuote()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartArray().write(1).writeStartEscaped().writeStartArray().write(2);
        generator.wrap(buffer, 0, 2);

        assertFalse(generator.writeStartEscapedEx());
        assertEquals(0, generator.length());
    }

    @Test
    void shouldBoundOutputOfStringInsideScope()
    {
        final JsonGeneratorEx generator = generator();
        generator.writeStartObject().writeKey("a").writeStartEscaped();
        generator.wrap(buffer, 0, 9);

        generator.write("a\"b\"c", JsonGeneratorEx.Completion.COMPLETE);

        assertEquals("\\\"a\\\\\\\"", output(generator));
    }

    private JsonGeneratorEx generator()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(buffer, 0, buffer.capacity());
        return generator;
    }

    private String output(
        JsonGeneratorEx generator)
    {
        final byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }
}
