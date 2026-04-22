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

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_FALSE;
import static jakarta.json.stream.JsonParser.Event.VALUE_NULL;
import static jakarta.json.stream.JsonParser.Event.VALUE_NUMBER;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static jakarta.json.stream.JsonParser.Event.VALUE_TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class StreamingJsonParserTest
{
    @Test
    void shouldParseFlatObject()
    {
        JsonParser parser = parserFor("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/list\"}");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("jsonrpc", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("2.0", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("id", parser.getString());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("42", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("method", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("tools/list", parser.getString());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseNestedArrays()
    {
        JsonParser parser = parserFor("{\"a\":[1,[2,3],{\"b\":true}]}");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("a", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("1", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("2", parser.getString());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("3", parser.getString());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("b", parser.getString());
        assertEquals(VALUE_TRUE, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseLiterals()
    {
        JsonParser parser = parserFor("[true,false,null]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_TRUE, parser.next());
        assertEquals(VALUE_FALSE, parser.next());
        assertEquals(VALUE_NULL, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldHandleEscapesInString()
    {
        JsonParser parser = parserFor("\"a\\nb\\tc\\\"\\u00e9\"");

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("a\nb\tc\"\u00e9", parser.getString());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldHandleAllStringEscapes()
    {
        JsonParser parser = parserFor("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"");

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("\"\\/\b\f\n\r\t", parser.getString());
    }

    @Test
    void shouldParseHexEscapeWithUppercaseDigits()
    {
        JsonParser parser = parserFor("\"\\u00AF\\u00bf\"");

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("\u00af\u00bf", parser.getString());
    }

    @Test
    void shouldPauseOnMidTokenEof()
    {
        byte[] full = "{\"image\":\"AAAA\"}".getBytes(UTF_8);
        for (int split = 1; split < full.length; split++)
        {
            DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
            UnsafeBufferEx buffer = new UnsafeBufferEx(full);

            in.wrap(buffer, 0, split);
            JsonParser parser = StreamingJson.createParser(in);
            while (parser.hasNext())
            {
                parser.next();
            }
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, full.length - committed);
            while (parser.hasNext())
            {
                parser.next();
            }
            assertEquals(full.length, parser.getLocation().getStreamOffset(),
                "failed at split=" + split);
        }
    }

    @Test
    void shouldHandleByteByByteFeed()
    {
        byte[] full = "{\"k\":[1,2,3],\"b\":true}".getBytes(UTF_8);
        UnsafeBufferEx buffer = new UnsafeBufferEx(full);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, 1);
        JsonParser parser = StreamingJson.createParser(in);

        int events = 0;
        for (int i = 1; i <= full.length; i++)
        {
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, i - committed);
            while (parser.hasNext())
            {
                parser.next();
                events++;
            }
        }
        assertEquals(10, events);
        assertEquals(full.length, parser.getLocation().getStreamOffset());
    }

    @Test
    void shouldTrackStreamOffset()
    {
        JsonParser parser = parserFor("{\"a\":1}");
        parser.next();
        assertEquals(1, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(4, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(6, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(7, parser.getLocation().getStreamOffset());
    }

    @Test
    void shouldParseNegativeInteger()
    {
        JsonParser parser = parserFor("[-42]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("-42", parser.getString());
        assertEquals(-42, parser.getInt());
        assertEquals(-42L, parser.getLong());
        assertTrue(parser.isIntegralNumber());
        assertEquals(END_ARRAY, parser.next());
    }

    @Test
    void shouldParseDecimalNumber()
    {
        JsonParser parser = parserFor("[3.14]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("3.14", parser.getString());
        assertFalse(parser.isIntegralNumber());
        assertEquals(new BigDecimal("3.14"), parser.getBigDecimal());
    }

    @Test
    void shouldParseExponentNumber()
    {
        JsonParser parser = parserFor("[1e10]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("1e10", parser.getString());
        assertFalse(parser.isIntegralNumber());
    }

    @Test
    void shouldParseNegativeExponentNumber()
    {
        JsonParser parser = parserFor("[-1.5E-3]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("-1.5E-3", parser.getString());
        assertFalse(parser.isIntegralNumber());
    }

    @Test
    void shouldParseLongNumber()
    {
        JsonParser parser = parserFor("[123456789012]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(123456789012L, parser.getLong());
    }

    @Test
    void shouldDecodeTwoByteUtf8()
    {
        byte[] bytes = {'"', (byte) 0xC3, (byte) 0xA9, '"'};

        JsonParser parser = parserFor(bytes);

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("\u00e9", parser.getString());
    }

    @Test
    void shouldDecodeThreeByteUtf8()
    {
        byte[] bytes = {'"', (byte) 0xE2, (byte) 0x82, (byte) 0xAC, '"'};

        JsonParser parser = parserFor(bytes);

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("\u20ac", parser.getString());
    }

    @Test
    void shouldDecodeFourByteUtf8()
    {
        byte[] bytes = {'"', (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80, '"'};

        JsonParser parser = parserFor(bytes);

        assertEquals(VALUE_STRING, parser.next());
        assertEquals(new String(Character.toChars(0x1F600)), parser.getString());
    }

    @Test
    void shouldParseEmptyObject()
    {
        JsonParser parser = parserFor("{}");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseEmptyArray()
    {
        JsonParser parser = parserFor("[]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseWithSurroundingWhitespace()
    {
        JsonParser parser = parserFor(" \t\n\r {\n \"a\" : 1 \n, \"b\" :\t[ 2 ]\n}\n ");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("a", parser.getString());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("b", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(END_OBJECT, parser.next());
    }

    @Test
    void shouldParseTopLevelString()
    {
        JsonParser parser = parserFor("\"hello\"");

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("hello", parser.getString());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseTopLevelNumberTerminatedByWhitespace()
    {
        JsonParser parser = parserFor("42 ");

        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("42", parser.getString());
    }

    @Test
    void shouldThrowOnNextBeyondEnd()
    {
        JsonParser parser = parserFor("{}");
        parser.next();
        parser.next();

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldReturnFalseHasNextBeyondEnd()
    {
        JsonParser parser = parserFor("[]");
        parser.next();
        parser.next();

        assertFalse(parser.hasNext());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldRejectUnexpectedStartCharacter()
    {
        JsonParser parser = parserFor("x");

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectUnquotedKey()
    {
        JsonParser parser = parserFor("{a:1}");

        assertThrows(JsonParsingException.class, () -> consumeAll(parser));
    }

    @Test
    void shouldRejectMissingColon()
    {
        JsonParser parser = parserFor("{\"a\" 1}");

        assertThrows(JsonParsingException.class, () -> consumeAll(parser));
    }

    @Test
    void shouldRejectMissingCommaOrEnd()
    {
        JsonParser parser = parserFor("[1 2]");

        assertThrows(JsonParsingException.class, () -> consumeAll(parser));
    }

    @Test
    void shouldRejectUnescapedControlCharacter()
    {
        byte[] bytes = {'"', 'a', 0x01, 'b', '"'};
        JsonParser parser = parserFor(bytes);

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectInvalidStringEscape()
    {
        JsonParser parser = parserFor("\"\\q\"");

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectInvalidUtf8LeadByte()
    {
        byte[] bytes = {'"', (byte) 0xF8, '"'};
        JsonParser parser = parserFor(bytes);

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectInvalidUtf8Continuation()
    {
        byte[] bytes = {'"', (byte) 0xC3, 'z', '"'};
        JsonParser parser = parserFor(bytes);

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectInvalidHexDigit()
    {
        JsonParser parser = parserFor("\"\\u00GZ\"");

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectUnexpectedLiteralCharacter()
    {
        JsonParser parser = parserFor("truX");

        assertThrows(JsonParsingException.class, parser::next);
    }

    @Test
    void shouldRejectStreamWithoutMarkSupport()
    {
        InputStream in = new InputStream()
        {
            @Override
            public int read()
            {
                return -1;
            }

            @Override
            public boolean markSupported()
            {
                return false;
            }
        };

        assertThrows(IllegalArgumentException.class, () -> StreamingJson.createParser(in));
    }

    @Test
    void shouldWrapIOExceptionAsJsonParsingException()
    {
        InputStream in = new InputStream()
        {
            @Override
            public boolean markSupported()
            {
                return true;
            }

            @Override
            public void mark(int readlimit)
            {
            }

            @Override
            public void reset() throws IOException
            {
                throw new IOException("boom-reset");
            }

            @Override
            public int read() throws IOException
            {
                throw new IOException("boom-read");
            }
        };

        JsonParser parser = StreamingJson.createParser(in);
        JsonParsingException ex = assertThrows(JsonParsingException.class, parser::hasNext);
        assertNotNull(ex.getCause());
    }

    @Test
    void shouldThrowOnGetObject()
    {
        JsonParser parser = parserFor("{}");

        assertThrows(UnsupportedOperationException.class, parser::getObject);
    }

    @Test
    void shouldThrowOnGetArray()
    {
        JsonParser parser = parserFor("[]");

        assertThrows(UnsupportedOperationException.class, parser::getArray);
    }

    @Test
    void shouldThrowOnGetValue()
    {
        JsonParser parser = parserFor("1");

        assertThrows(UnsupportedOperationException.class, parser::getValue);
    }

    @Test
    void shouldThrowOnGetObjectStream()
    {
        JsonParser parser = parserFor("{}");

        assertThrows(UnsupportedOperationException.class, parser::getObjectStream);
    }

    @Test
    void shouldThrowOnGetArrayStream()
    {
        JsonParser parser = parserFor("[]");

        assertThrows(UnsupportedOperationException.class, parser::getArrayStream);
    }

    @Test
    void shouldThrowOnGetValueStream()
    {
        JsonParser parser = parserFor("1");

        assertThrows(UnsupportedOperationException.class, parser::getValueStream);
    }

    @Test
    void shouldThrowOnSkipObject()
    {
        JsonParser parser = parserFor("{}");

        assertThrows(UnsupportedOperationException.class, parser::skipObject);
    }

    @Test
    void shouldThrowOnSkipArray()
    {
        JsonParser parser = parserFor("[]");

        assertThrows(UnsupportedOperationException.class, parser::skipArray);
    }

    @Test
    void shouldSupportClose()
    {
        JsonParser parser = parserFor("{}");

        parser.close();
    }

    @Test
    void shouldThrowIsIntegralOnNonNumberEvent()
    {
        JsonParser parser = parserFor("true");
        parser.next();

        assertThrows(IllegalStateException.class, parser::isIntegralNumber);
    }

    @Test
    void shouldResumeInsideUnicodeEscape()
    {
        byte[] full = "\"\\u00e9!\"".getBytes(UTF_8);
        for (int split = 1; split < full.length; split++)
        {
            UnsafeBufferEx buffer = new UnsafeBufferEx(full);
            DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
            in.wrap(buffer, 0, split);
            JsonParser parser = StreamingJson.createParser(in);
            while (parser.hasNext())
            {
                parser.next();
            }
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, full.length - committed);
            assertTrue(parser.hasNext(), "failed at split=" + split);
            assertEquals(VALUE_STRING, parser.next());
            assertEquals("\u00e9!", parser.getString());
            assertFalse(parser.hasNext(), "failed at split=" + split);
        }
    }

    @Test
    void shouldResumeInsideNumber()
    {
        byte[] full = "-123.45e6 ".getBytes(UTF_8);
        for (int split = 1; split < full.length; split++)
        {
            UnsafeBufferEx buffer = new UnsafeBufferEx(full);
            DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
            in.wrap(buffer, 0, split);
            JsonParser parser = StreamingJson.createParser(in);
            while (parser.hasNext())
            {
                parser.next();
            }
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, full.length - committed);
            assertTrue(parser.hasNext(), "failed at split=" + split);
            assertEquals(VALUE_NUMBER, parser.next());
            assertEquals("-123.45e6", parser.getString());
        }
    }

    private static void consumeAll(
        JsonParser parser)
    {
        while (parser.hasNext())
        {
            parser.next();
        }
    }

    private static JsonParser parserFor(
        String text)
    {
        return parserFor(text.getBytes(UTF_8));
    }

    private static JsonParser parserFor(
        byte[] bytes)
    {
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
