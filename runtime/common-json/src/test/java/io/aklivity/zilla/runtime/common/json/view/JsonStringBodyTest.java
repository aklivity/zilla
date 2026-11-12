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
package io.aklivity.zilla.runtime.common.json.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.Random;

import jakarta.json.stream.JsonParser.Event;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;

public class JsonStringBodyTest
{
    private final JsonStringBody body = new JsonStringBody();
    private final JsonStringView.Decoded decoded = new JsonStringView.Decoded();
    private final JsonStringView.Encoded encoded = new JsonStringView.Encoded();

    @Test
    void shouldEscapeQuoteBackslashAndControlChars()
    {
        assertEquals("\\\"\\\\\\b\\f\\n\\r\\t\\u0001\\u001f", body("\"\\\b\f\n\r\t\u0001\u001f", 0, 9));
        assertEquals(7 * 2 + 2 * 6, length("\"\\\b\f\n\r\t\u0001\u001f", 0, 9));
    }

    @Test
    void shouldEncodeNonAsciiAsUtf8()
    {
        final String text = "é€😀";

        assertEquals(text, body(text, 0, text.length()));
        assertEquals(2 + 3 + 4, length(text, 0, text.length()));
    }

    @Test
    void shouldReplaceALoneSurrogateWithTheReplacementChar()
    {
        assertEquals("a�b�", body("a\ud800b\udc00", 0, 4));
    }

    @Test
    void shouldTransferARange()
    {
        assertEquals("bc\\n", body("abc\nd", 1, 4));
        assertEquals("", body("abc", 2, 2));
    }

    @Test
    void shouldRejectARangeThatSplitsASurrogatePair()
    {
        decoded.wrap("a😀b");

        assertThrows(AssertionError.class, () -> body.length(decoded, 0, 2));
        assertThrows(AssertionError.class, () -> body.length(decoded, 2, 4));
    }

    @Test
    void shouldHandOverEncodedBytesAsTheyAre()
    {
        final byte[] bytes = "a\\né".getBytes(UTF_8);

        encoded.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, 3, false);

        final byte[] out = new byte[16];
        final UnsafeBufferEx buffer = new UnsafeBufferEx(out);
        final int length = body.transfer(encoded, 0, encoded.length(), new SegmentTransferTarget()
            .wrap(buffer.segment(), buffer.wrapAdjustment()));

        assertEquals("a\\né", new String(out, 0, length, UTF_8));
        assertEquals(length, body.length(encoded, 0, encoded.length()));
    }

    @Test
    void shouldTransferEverythingAParserReadsBackTheSame()
    {
        final Random random = new Random(11L);

        for (int round = 0; round < 500; round++)
        {
            final StringBuilder chars = new StringBuilder();

            for (int i = 0, length = random.nextInt(40); i < length; i++)
            {
                final int kind = random.nextInt(6);
                chars.append(switch (kind)
                {
                case 0 -> (char) random.nextInt(0x20);
                case 1 -> "\"\\/".charAt(random.nextInt(3));
                case 2 -> (char) (0x20 + random.nextInt(0x5f));
                case 3 -> (char) (0xa0 + random.nextInt(0x2000));
                case 4 -> "😀";
                default -> (char) ('a' + random.nextInt(26));
                });
            }

            final String text = chars.toString();
            final String escaped = body(text, 0, text.length());
            final JsonParserEx parser = JsonEx.createParser(new ByteArrayInputStream(("[\"" + escaped + "\"]").getBytes(UTF_8)));

            assertEquals(Event.START_ARRAY, parser.next());
            assertEquals(Event.VALUE_STRING, parser.next());
            assertEquals(text, parser.getString(), "round " + round);
            assertEquals(escaped.getBytes(UTF_8).length, length(text, 0, text.length()));
        }
    }

    private int length(
        CharSequence chars,
        int fromChar,
        int toChar)
    {
        return body.length(decoded.wrap(chars), fromChar, toChar);
    }

    private String body(
        CharSequence chars,
        int fromChar,
        int toChar)
    {
        final byte[] bytes = new byte[256];
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(buffer.segment(), buffer.wrapAdjustment());

        final int length = body.transfer(decoded.wrap(chars), fromChar, toChar, target);

        return new String(bytes, 0, length, UTF_8);
    }
}
