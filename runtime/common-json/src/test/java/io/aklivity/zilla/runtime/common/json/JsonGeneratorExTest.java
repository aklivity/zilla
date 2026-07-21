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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Consumer;

import jakarta.json.JsonException;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;

class JsonGeneratorExTest
{
    @Test
    void shouldWriteEmptyObject()
    {
        assertEquals("{}", generate(g -> g.writeStartObject().writeEnd()));
    }

    @Test
    void shouldWriteEmptyArray()
    {
        assertEquals("[]", generate(g -> g.writeStartArray().writeEnd()));
    }

    @Test
    void shouldWriteFlatObject()
    {
        assertEquals("{\"a\":1,\"b\":\"x\"}", generate(g -> g
            .writeStartObject()
            .writeKey("a").writeNumber("1")
            .writeKey("b").write("x")
            .writeEnd()));
    }

    @Test
    void shouldWriteScalarArray()
    {
        assertEquals("[1,true,null,\"x\"]", generate(g -> g
            .writeStartArray()
            .writeNumber("1")
            .write(true)
            .writeNull()
            .write("x")
            .writeEnd()));
    }

    @Test
    void shouldWriteNestedStructures()
    {
        assertEquals("{\"a\":[1,2,{\"b\":true}],\"c\":null}", generate(g -> g
            .writeStartObject()
            .writeKey("a").writeStartArray()
                .writeNumber("1")
                .writeNumber("2")
                .writeStartObject().writeKey("b").write(true).writeEnd()
            .writeEnd()
            .writeKey("c").writeNull()
            .writeEnd()));
    }

    @Test
    void shouldWriteNamedValues()
    {
        assertEquals("{\"a\":1,\"b\":2,\"c\":1.5,\"d\":true,\"e\":null,\"f\":\"x\"}", generate(g -> g
            .writeStartObject()
            .write("a", 1)
            .write("b", 2L)
            .write("c", 1.5)
            .write("d", true)
            .writeNull("e")
            .write("f", "x")
            .writeEnd()));
    }

    @Test
    void shouldWriteBigNumbers()
    {
        assertEquals("[12345678901234567890,1.50]", generate(g -> g
            .writeStartArray()
            .write(new BigInteger("12345678901234567890"))
            .write(new BigDecimal("1.50"))
            .writeEnd()));
        assertEquals("{\"i\":7,\"d\":2.5}", generate(g -> g
            .writeStartObject()
            .write("i", BigInteger.valueOf(7))
            .write("d", new BigDecimal("2.5"))
            .writeEnd()));
    }

    @Test
    void shouldWriteSignedIntBoundaries()
    {
        assertEquals("[0,-1,2147483647,-2147483648]", generate(g -> g
            .writeStartArray()
            .write(0)
            .write(-1)
            .write(Integer.MAX_VALUE)
            .write(Integer.MIN_VALUE)
            .writeEnd()));
    }

    @Test
    void shouldWriteSignedLongBoundaries()
    {
        assertEquals("[0,-1,9223372036854775807,-9223372036854775808]", generate(g -> g
            .writeStartArray()
            .write(0L)
            .write(-1L)
            .write(Long.MAX_VALUE)
            .write(Long.MIN_VALUE)
            .writeEnd()));
    }

    @Test
    void shouldRejectNonFiniteDouble()
    {
        assertThrows(NumberFormatException.class, () -> generate(g -> g.write(Double.NaN)));
        assertThrows(NumberFormatException.class, () -> generate(g -> g.write(Double.POSITIVE_INFINITY)));
    }

    @Test
    void shouldWriteJsonValues()
    {
        assertEquals("true", generate(g -> g.write(JsonValue.TRUE)));
        assertEquals("{\"a\":null}", generate(g -> g
            .writeStartObject().write("a", JsonValue.NULL).writeEnd()));
    }

    @Test
    void shouldWriteNumberForms()
    {
        assertEquals("[-1,1.5,-2.5e3]", generate(g -> g
            .writeStartArray()
            .writeNumber("-1")
            .writeNumber("1.5")
            .writeNumber("-2.5e3")
            .writeEnd()));
    }

    @Test
    void shouldEscapeControlAndSpecialCharacters()
    {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\r\\b\\f\\u0001\"", generate(g ->
            g.write("a\"b\\c\nd\te\r\b\f")));
    }

    @Test
    void shouldEncodeMultibyteUtf8()
    {
        assertEquals("\"é€😀\"", generate(g -> g.write("é€😀")));
    }

    @Test
    void shouldWriteTopLevelScalar()
    {
        assertEquals("\"hello\"", generate(g -> g.write("hello")));
    }

    @Test
    void shouldWriteRawValueVerbatim()
    {
        MutableDirectBufferEx source = new UnsafeBufferEx("42".getBytes(UTF_8));
        assertEquals("[42]", generate(g -> g
            .writeStartArray()
            .writeRaw(source, 0, 2)
            .writeEnd()));
    }

    @Test
    void shouldReportLength()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        generator.writeStartObject().writeKey("a").writeNumber("1").writeEnd();
        assertEquals("{\"a\":1}".length(), generator.length());
    }

    @Test
    void shouldReportRemainingWithinBound()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, 16);
        assertEquals(16, generator.remaining());
        generator.writeStartArray().writeNumber("1");
        assertEquals(2, generator.length());
        assertEquals(14, generator.remaining());
    }

    @Test
    void shouldPreserveContextAcrossBoundedRewrap()
    {
        MutableDirectBufferEx first = new UnsafeBufferEx(new byte[32]);
        MutableDirectBufferEx second = new UnsafeBufferEx(new byte[32]);
        JsonGeneratorEx generator = JsonEx.createGenerator();

        generator.wrap(first, 0, 32).writeStartArray().writeNumber("1");
        String chunk1 = drain(generator, first);

        generator.wrap(second, 0, 32).writeNumber("2").writeEnd();
        String chunk2 = drain(generator, second);

        assertEquals("[1,2]", chunk1 + chunk2);
    }

    @Test
    void shouldNotWriteBeyondLimit()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, 4);
        // "[1,2" fills the usable region [0,4) exactly; the next writeNumber's leading separator has no
        // room, so it must defer rather than spill the comma past the limit
        generator.writeStartArray().writeNumber("1").writeNumber("2").writeNumber("3");

        assertEquals(4, generator.length());
        assertEquals("[1,2", drain(generator, buffer));
    }

    @Test
    void shouldNotWriteNumberSeparatorWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        generator.writeStartArray().writeNumber("1");
        assertEquals(2, generator.length());

        generator.wrap(buffer, 32, 32);
        generator.writeNumber("2");

        assertEquals(0, generator.length());
    }

    @Test
    void shouldNotWriteStartObjectWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        // top-level, so no leading separator applies: a zero-width wrap has no room for the brace itself
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32);

        assertFalse(generator.writeStartObjectEx());

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldThrowWritingStartObjectWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32);

        assertThrows(JsonException.class, generator::writeStartObject);
    }

    @Test
    void shouldNotWriteStartArrayWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32);

        assertFalse(generator.writeStartArrayEx());

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldNotWriteEndWithoutRoom()
    {
        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(roomy, 0, roomy.capacity());
        generator.writeStartObject();

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);

        assertFalse(generator.writeEndEx());

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        tiny.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldNotWriteBooleanWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        // top-level "true" needs exactly 4 bytes with no leading separator; one byte short must defer
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32 + "true".length() - 1);

        assertFalse(generator.writeEx(true));

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldNotWriteNullWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32 + "null".length() - 1);

        assertFalse(generator.writeNullEx());

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldNotWriteIntWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        String lexeme = Integer.toString(Integer.MIN_VALUE);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32 + lexeme.length() - 1);

        assertFalse(generator.writeEx(Integer.MIN_VALUE));

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldNotWriteLongWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        String lexeme = Long.toString(Long.MIN_VALUE);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32 + lexeme.length() - 1);

        assertFalse(generator.writeEx(Long.MIN_VALUE));

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldResumeStructuralWriteOnceRoomIsAvailable()
    {
        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(roomy, 0, roomy.capacity());
        generator.writeStartObject();

        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        generator.wrap(tiny, 32, 32);
        assertFalse(generator.writeEndEx());
        assertEquals(0, generator.length());

        MutableDirectBufferEx fresh = new UnsafeBufferEx(new byte[64]);
        generator.wrap(fresh, 0, fresh.capacity());
        assertTrue(generator.writeEndEx());

        assertEquals("}", drain(generator, fresh));
    }

    @Test
    void shouldNotWriteStringOpeningQuoteWithoutRoom()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 32, 32);

        generator.write("x");

        assertEquals(0, generator.length());
        assertEquals(0, generator.consumed());
        byte[] untouched = new byte[1];
        buffer.getBytes(32, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldDeferEmptyCompleteStringWithoutRoomForBothQuotes()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 31, 32);

        generator.write("", Completion.COMPLETE);

        assertEquals(0, generator.length());
        byte[] untouched = new byte[1];
        buffer.getBytes(31, untouched);
        assertEquals(0, untouched[0]);
    }

    @Test
    void shouldWriteEmptyCompleteStringWhenExactRoomForBothQuotes()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 30, 32);

        generator.write("", Completion.COMPLETE);

        assertEquals(2, generator.length());
        byte[] out = new byte[2];
        buffer.getBytes(30, out);
        assertEquals("\"\"", new String(out, UTF_8));
    }

    @Test
    void shouldResumeStringOpeningQuoteOnceRoomIsAvailable()
    {
        MutableDirectBufferEx tight = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(tight, 32, 32);
        generator.write("hello");
        assertEquals(0, generator.consumed());

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());
        generator.write("hello");

        assertEquals("\"hello\"", drain(generator, roomy));
    }

    @Test
    void shouldReportPartialConsumptionWhenPlainWriteExceedsLimit()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, 8);

        generator.write("abcdefghij");

        int written = generator.consumed();
        assertTrue(written > 0 && written < "abcdefghij".length());
        assertEquals("\"" + "abcdefghij".substring(0, written), drain(generator, buffer));
    }

    @Test
    void shouldReportPartialConsumptionWhenPlainWriteKeyExceedsLimit()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, 6);

        generator.writeStartObject().writeKey("abcdefgh");

        int written = generator.consumed();
        assertTrue(written > 0 && written < "abcdefgh".length());
    }

    @Test
    void shouldReportPartialConsumptionWhenPlainWriteNumberExceedsLimit()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, 3);

        generator.writeNumber("123456789");

        int written = generator.consumed();
        assertTrue(written > 0 && written < "123456789".length());
    }

    @Test
    void shouldAllowResumingAPlainWriteThatDidNotFullyFit()
    {
        MutableDirectBufferEx first = new UnsafeBufferEx(new byte[8]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(first, 0, 8);

        generator.write("abcdefghij");
        int firstConsumed = generator.consumed();
        String chunk1 = drain(generator, first);

        MutableDirectBufferEx second = new UnsafeBufferEx(new byte[32]);
        generator.wrap(second, 0, 32);
        generator.write("abcdefghij".substring(firstConsumed), Completion.COMPLETE);
        String chunk2 = drain(generator, second);

        assertEquals("\"abcdefghij\"", chunk1 + chunk2);
    }

    @Test
    void shouldClearContextOnReset()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(buffer, 0, buffer.capacity()).writeStartArray().writeNumber("1");
        generator.reset();
        generator.wrap(buffer, 0, buffer.capacity()).writeStartObject().writeEnd();
        assertEquals("{}", drain(generator, buffer));
    }

    @Test
    void shouldWriteAtNonZeroOffset()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 8, buffer.capacity());
        generator.writeStartArray().writeNumber("1").writeEnd();
        byte[] out = new byte[generator.length()];
        buffer.getBytes(8, out);
        assertEquals("[1]", new String(out, UTF_8));
    }

    @Test
    void shouldWriteDeferredStringAcrossFragments()
    {
        assertEquals("[\"abcd\"]", generate(g -> g
            .writeStartArray()
            .write("ab", Completion.INCOMPLETE)
            .write("cd", Completion.COMPLETE)
            .writeEnd()));
    }

    @Test
    void shouldWriteDeferredStringWithEscapesAcrossFragments()
    {
        // a fragment boundary between escapable chars must still produce one correctly escaped string
        assertEquals(
            generate(g -> g.writeStartArray().write("a\"b\nc").writeEnd()),
            generate(g -> g.writeStartArray()
                .write("a\"b", Completion.INCOMPLETE)
                .write("\nc", Completion.COMPLETE)
                .writeEnd()));
    }

    private static String generate(
        Consumer<JsonGeneratorEx> writer)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[512]);
        JsonGeneratorEx generator = JsonEx.createGenerator().wrap(buffer, 0, buffer.capacity());
        writer.accept(generator);
        return drain(generator, buffer);
    }

    private static String drain(
        JsonGeneratorEx generator,
        MutableDirectBufferEx buffer)
    {
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
