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

public class DecodedStringViewTest
{
    private final DecodedStringView view = new DecodedStringView();

    @Test
    void shouldReadTheCharsItWraps()
    {
        view.wrap("a\"b");

        assertEquals(3, view.length());
        assertEquals('"', view.charAt(1));
        assertEquals("\"b", view.subSequence(1, 3).toString());
        assertEquals("a\"b", view.toString());
    }

    @Test
    void shouldTransferPlainCharsAsTheyAre()
    {
        assertEquals("plain 7f\u007f", transferred("plain 7f\u007f", 0, 9));
        assertEquals(9, view.transferLength(0, 9));
    }

    @Test
    void shouldEscapeQuoteBackslashAndControlChars()
    {
        assertEquals("\\\"\\\\\\b\\f\\n\\r\\t\\u0001\\u001f", transferred("\"\\\b\f\n\r\t\u0001\u001f", 0, 9));
    }

    @Test
    void shouldEncodeNonAsciiAsUtf8()
    {
        final String text = "é€😀";

        assertEquals(text, transferred(text, 0, text.length()));
        assertEquals(2 + 3 + 4, view.transferLength(0, text.length()));
    }

    @Test
    void shouldReplaceALoneSurrogateWithTheReplacementChar()
    {
        assertEquals("a�b�", transferred("a\ud800b\udc00", 0, 4));
        assertEquals(1 + 3 + 1 + 3, view.transferLength(0, 4));
    }

    @Test
    void shouldTransferARange()
    {
        assertEquals("bc\\n", transferred("abc\nd", 1, 4));
        assertEquals("", transferred("abc", 2, 2));
    }

    @Test
    void shouldRejectARangeThatIsNotWithinTheChars()
    {
        view.wrap("abc");

        assertThrows(IndexOutOfBoundsException.class, () -> view.transferLength(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> view.transferLength(1, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> view.transferLength(2, 1));
    }

    @Test
    void shouldRejectARangeThatSplitsASurrogatePair()
    {
        view.wrap("a😀b");

        assertThrows(IllegalArgumentException.class, () -> view.transferLength(0, 2));
        assertThrows(IllegalArgumentException.class, () -> view.transferLength(2, 4));
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
            final String body = transferred(text, 0, text.length());
            final JsonParserEx parser = JsonEx.createParser(new ByteArrayInputStream(("[\"" + body + "\"]").getBytes(UTF_8)));

            assertEquals(Event.START_ARRAY, parser.next());
            assertEquals(Event.VALUE_STRING, parser.next());
            assertEquals(text, parser.getString(), "round " + round);
            assertEquals(body.getBytes(UTF_8).length, view.transferLength(0, text.length()));
        }
    }

    private String transferred(
        CharSequence chars,
        int fromChar,
        int toChar)
    {
        final byte[] bytes = new byte[256];
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(buffer.segment(), buffer.wrapAdjustment());

        view.wrap(chars);

        final int length = view.transferTo(fromChar, toChar, target);

        return new String(bytes, 0, length, UTF_8);
    }
}
