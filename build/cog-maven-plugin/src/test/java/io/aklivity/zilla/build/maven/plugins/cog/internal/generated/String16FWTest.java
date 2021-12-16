/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collection;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String16FW;

@RunWith(Parameterized.class)
public class String16FWTest
{
    private static final int LENGTH_SIZE = 2;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100000))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    @Parameters
    public static Collection<Object[]> values()
    {
        return Arrays.asList(
                new Object[][]
                {
                    { new String16FW.Builder(), new String16FW() },
                    { new String16FW.Builder(LITTLE_ENDIAN), new String16FW(LITTLE_ENDIAN) },
                    { new String16FW.Builder(BIG_ENDIAN), new String16FW(BIG_ENDIAN) }
                });
    }

    @Parameter(0)
    public String16FW.Builder stringRW;

    @Parameter(1)
    public String16FW stringRO;

    @Test
    public void shouldInitWithString() throws Exception
    {
        String16FW string = new String16FW("test");

        assertEquals("test", string.asString());
        assertEquals(LENGTH_SIZE + "test".length(), string.sizeof());
    }

    @Test
    public void shouldInitWithEmptyString() throws Exception
    {
        String16FW string = new String16FW("");

        assertEquals("", string.asString());
        assertEquals(LENGTH_SIZE, string.sizeof());
    }

    @Test
    public void shouldInitWithNullString() throws Exception
    {
        String16FW string = new String16FW((String) null);

        assertNull(string.asString());
        assertEquals(LENGTH_SIZE, string.sizeof());
    }

    @Test
    public void shouldInitWithStringAndCharset() throws Exception
    {
        String16FW string = new String16FW("test", UTF_8);

        assertEquals("test", string.asString());
        assertEquals(LENGTH_SIZE + "test".length(), string.sizeof());
    }

    @Test
    public void shouldDefaultAfterRewrap() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                .set("Hello, world", UTF_8)
                .build()
                .limit();

        String16FW string = stringRW.wrap(buffer, 0, limit)
                .build();

        assertNull(string.asString());
        assertNull(string.value());
        assertEquals(LENGTH_SIZE, string.limit());
        assertEquals(LENGTH_SIZE, string.sizeof());
    }

    @Test
    public void shouldDefaultToEmpty() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                .build()
                .limit();
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(LENGTH_SIZE, stringRO.limit());
        assertEquals(LENGTH_SIZE, stringRO.sizeof());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToWrapInBuilderWithInsufficientLengthOffsetEqualsMaxLimit()
    {
        stringRW.wrap(buffer, 10, 10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapInBuilderWithInufficientLength()
    {
        stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE - 1);
    }

    @Test
    public void shouldWrapInBuilderWithSufficientLength()
    {
        stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE);
    }

    @Test
    public void shouldReturnNullFromTryWrapWithOffsetEqualsMaxLimit()
    {
        assertNull(stringRO.tryWrap(buffer, 10, 10));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToWrapWithOffsetEqualsMaxLimit()
    {
        stringRO.wrap(buffer, 10, 10);
    }

    @Test
    public void shouldReturnNullFromTryWrapWithInsufficientSize()
    {
        assertNull(stringRO.tryWrap(buffer, 10, 10 + LENGTH_SIZE - 1));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapWithInsufficientLength()
    {
        stringRO.wrap(buffer, 10, 10 + LENGTH_SIZE - 1);
    }

    @Test
    public void shouldReturnNullFromTryWrapWithInsufficientLength()
    {
        buffer.putShort(10,  (short) 1);
        assertNull(stringRO.tryWrap(buffer, 10, 10 + LENGTH_SIZE));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToWrapWithInsufficientLength()
    {
        buffer.putShort(10,  (short) 1);
        stringRO.wrap(buffer, 10, 10 + LENGTH_SIZE);
    }

    @Test
    public void shouldTryWrapStringWithLengthZero()
    {
        buffer.putShort(10,  (short) 0);
        assertEquals(stringRO, stringRO.tryWrap(buffer, 10, 12));
    }

    @Test
    public void shouldWrapStringWithLengthZero()
    {
        buffer.putShort(10,  (short) 0);
        stringRO.wrap(buffer, 10, 12);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingStringWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        try
        {
            stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE)
                .set("1", UTF_8);
        }
        finally
        {
            byte[] bytes = new byte[1 + LENGTH_SIZE];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(10 + LENGTH_SIZE));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingStringFWWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        try
        {
            stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE)
                .set(asStringFW("1"));
        }
        finally
        {
            byte[] bytes = new byte[1 + LENGTH_SIZE];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(10 + LENGTH_SIZE));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingBufferWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        buffer.putStringWithoutLengthUtf8(0, "1");
        try
        {
            stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE)
                .set(buffer, 0, 1);
        }
        finally
        {
            byte[] bytes = new byte[1 + LENGTH_SIZE];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(10 + LENGTH_SIZE));
        }
    }

    @Test
    public void shouldSetToNull() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                            .set(new String16FW((String)null))
                            .build()
                            .limit();
        assertEquals(2, limit);
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(LENGTH_SIZE, stringRO.limit());
        assertEquals(LENGTH_SIZE, stringRO.sizeof());
        assertNull(stringRO.asString());
        assertNull(stringRO.value());
    }

    @Test
    public void shouldSetToNullUsingStringSetter() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                            .set(null, UTF_8)
                            .build()
                            .limit();
        assertEquals(2, limit);
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(LENGTH_SIZE, stringRO.limit());
        assertEquals(LENGTH_SIZE, stringRO.sizeof());
        assertNull(stringRO.asString());
        assertNull(stringRO.value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToBuildLargeString() throws Exception
    {
        String str = String.format("%65535s", "0");
        stringRW.wrap(buffer, 0, buffer.capacity())
                .set(str, UTF_8);
    }

    @Test
    public void shouldBuildLargeString() throws Exception
    {
        String str = String.format("%65534s", "0");
        stringRW.wrap(buffer, 0, buffer.capacity())
                .set(str, UTF_8);
    }

    @Test
    public void shouldSetToEmptyString() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                .set("", UTF_8)
                .build()
                .limit();
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(LENGTH_SIZE, stringRO.limit());
        assertEquals(LENGTH_SIZE, stringRO.sizeof());
        assertEquals("", stringRO.asString());
        assertEquals("", stringRO.value().getStringWithoutLengthUtf8(0, stringRO.value().capacity()));
    }

    @Test
    public void shouldSetUsingString() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, buffer.capacity())
                .set("value1", UTF_8)
                .build()
                .limit();
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(6 + LENGTH_SIZE, stringRO.limit());
        assertEquals(6 + LENGTH_SIZE, stringRO.sizeof());
        assertEquals("value1", stringRO.asString());
        assertEquals("value1", stringRO.value().getStringWithoutLengthUtf8(0, stringRO.value().capacity()));
    }

    @Test
    public void shouldSetUsingStringFW() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, 50)
                .set(asStringFW("value1"))
                .build()
                .limit();
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(6 + LENGTH_SIZE, stringRO.limit());
        assertEquals(6 + LENGTH_SIZE, stringRO.sizeof());
        assertEquals("value1", stringRO.asString());
        assertEquals("value1", stringRO.value().getStringWithoutLengthUtf8(0, stringRO.value().capacity()));
    }

    @Test
    public void shouldSetUsingBuffer() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, 50)
            .set(asBuffer("value1"), 0, 6)
            .build()
            .limit();
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(6 + LENGTH_SIZE, stringRO.limit());
        assertEquals(6 + LENGTH_SIZE, stringRO.sizeof());
        assertEquals("value1", stringRO.asString());
        assertEquals("value1", stringRO.value().getStringWithoutLengthUtf8(0, stringRO.value().capacity()));
    }

    private static MutableDirectBuffer asBuffer(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(value.length()));
        buffer.putStringWithoutLengthUtf8(0, value);
        return buffer;
    }

    private static String16FW asStringFW(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String16FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }

}
