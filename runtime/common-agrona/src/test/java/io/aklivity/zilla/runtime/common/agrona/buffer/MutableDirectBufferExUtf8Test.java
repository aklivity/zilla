/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.common.agrona.buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MutableDirectBufferExUtf8Test
{
    private static final String REPLACEMENT = "�";

    @Parameters(name = "{0}")
    public static Collection<Object[]> buffers()
    {
        return List.of(
            new Object[] {"unsafe-heap", (Supplier<MutableDirectBufferEx>) () -> new UnsafeBufferEx(new byte[64])},
            new Object[] {"unsafe-native", (Supplier<MutableDirectBufferEx>) () ->
                new UnsafeBufferEx(MemorySegment.ofBuffer(ByteBuffer.allocateDirect(64)))},
            new Object[] {"unsafe-native-view", (Supplier<MutableDirectBufferEx>) () ->
                new UnsafeBufferEx(ByteBuffer.allocateDirect(64)).asNative()},
            new Object[] {"safe", (Supplier<MutableDirectBufferEx>) () -> new SafeBuffer(new byte[64])},
            new Object[] {"expandable-array", (Supplier<MutableDirectBufferEx>) () -> new ExpandableArrayBufferEx(64)},
            new Object[] {"expandable-direct", (Supplier<MutableDirectBufferEx>) () -> new ExpandableDirectByteBufferEx(64)});
    }

    @Parameter(0)
    public String name;

    @Parameter(1)
    public Supplier<MutableDirectBufferEx> factory;

    @Test
    public void shouldPutAsciiRange()
    {
        assertPut("hello world", 6, 11, "world");
    }

    @Test
    public void shouldPutTwoByteAndThreeByteCodePoints()
    {
        assertPut("café €", 0, 6, "café €");
    }

    @Test
    public void shouldPutSupplementaryCodePoint()
    {
        assertPut("a😀b", 0, 4, "a😀b");
    }

    @Test
    public void shouldPutLoneSurrogatesAsReplacement()
    {
        assertPut("a\ud83db", 0, 3, "a" + REPLACEMENT + "b");
        assertPut("a\ude00b", 0, 3, "a" + REPLACEMENT + "b");
        assertPut("ab\ud83d", 0, 3, "ab" + REPLACEMENT);
    }

    @Test
    public void shouldPutHighSurrogateSplitByRangeEnd()
    {
        assertPut("a😀", 0, 2, "a" + REPLACEMENT);
    }

    @Test
    public void shouldPutNothingForEmptyRange()
    {
        assertPut("abc", 1, 1, "");
    }

    @Test
    public void shouldPutAtIndexWithoutDisturbingNeighbours()
    {
        final MutableDirectBufferEx buffer = factory.get();
        buffer.putByte(7, (byte) 0x55);
        buffer.putByte(12, (byte) 0x66);

        final int written = buffer.putStringWithoutLengthUtf8(8, "abé", 0, 3);

        assertEquals(4, written);
        assertEquals(0x55, buffer.getByte(7));
        assertEquals(0x66, buffer.getByte(12));
        assertEquals("abé", buffer.getStringWithoutLengthUtf8(8, written));
    }

    @Test
    public void shouldPutFromCharSequenceThatIsNotAString()
    {
        final MutableDirectBufferEx buffer = factory.get();
        final StringBuilder chars = new StringBuilder("xy€z");

        final int written = buffer.putStringWithoutLengthUtf8(0, chars, 1, 4);

        assertEquals("y€z", buffer.getStringWithoutLengthUtf8(0, written));
    }

    @Test
    public void shouldMeasureUtf8Length()
    {
        assertEquals(0, MutableDirectBufferEx.utf8Length("abc", 1, 1));
        assertEquals(5, MutableDirectBufferEx.utf8Length("café ", 0, 4));
        assertEquals(3, MutableDirectBufferEx.utf8Length("€", 0, 1));
        assertEquals(4, MutableDirectBufferEx.utf8Length("😀", 0, 2));
        assertEquals(3, MutableDirectBufferEx.utf8Length("\ud83d", 0, 1));
        assertEquals(3, MutableDirectBufferEx.utf8Length("\ude00", 0, 1));
        assertEquals(3, MutableDirectBufferEx.utf8Length("😀", 0, 1));
    }

    @Test
    public void shouldMeasureExactlyWhatIsPut()
    {
        final String text = "aé€😀\ud83d!\ude00";

        for (int to = 0; to <= text.length(); to++)
        {
            final MutableDirectBufferEx buffer = factory.get();
            assertEquals(MutableDirectBufferEx.utf8Length(text, 0, to), buffer.putStringWithoutLengthUtf8(0, text, 0, to));
        }
    }

    private void assertPut(
        String chars,
        int from,
        int to,
        String expected)
    {
        final MutableDirectBufferEx buffer = factory.get();

        final int written = buffer.putStringWithoutLengthUtf8(0, chars, from, to);

        final byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        final byte[] actualBytes = new byte[written];
        buffer.getBytes(0, actualBytes);
        assertArrayEquals(expectedBytes, actualBytes);
        assertEquals(expectedBytes.length, MutableDirectBufferEx.utf8Length(chars, from, to));
    }
}
