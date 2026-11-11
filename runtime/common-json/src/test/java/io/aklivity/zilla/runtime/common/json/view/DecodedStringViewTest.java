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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;

public class DecodedStringViewTest
{
    private final JsonStringView.Decoded view = new JsonStringView.Decoded();

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
    void shouldTransferQuoteBackslashAndControlCharsWithoutEscapes()
    {
        assertEquals("\"\\\b\f\n\r\t\u0001\u001f", transferred("\"\\\b\f\n\r\t\u0001\u001f", 0, 9));
    }

    @Test
    void shouldBeDecoded()
    {
        assertEquals(JsonStringView.Kind.DECODED, view.kind());
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
        assertEquals("bc\n", transferred("abc\nd", 1, 4));
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
