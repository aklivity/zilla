/*
 * Copyright 2021-2022 Aklivity Inc.
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.String8FW;

public class String8FWTest
{
    private static final int LENGTH_SIZE = 1;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final String8FW.Builder stringRW = new String8FW.Builder();
    private final String8FW stringRO = new String8FW();

    @Test
    public void shouldInitWithString() throws Exception
    {
        String8FW string = new String8FW("test");

        assertEquals("test", string.asString());
        assertEquals(LENGTH_SIZE + "test".length(), string.sizeof());
    }

    @Test
    public void shouldInitWithEmptyString() throws Exception
    {
        String8FW string = new String8FW("");

        assertEquals("", string.asString());
        assertEquals(LENGTH_SIZE, string.sizeof());
    }

    @Test
    public void shouldInitWithNullString() throws Exception
    {
        String8FW string = new String8FW(null);

        assertNull(string.asString());
        assertEquals(LENGTH_SIZE, string.sizeof());
    }

    @Test
    public void shouldInitWithStringAndCharset() throws Exception
    {
        String8FW string = new String8FW("test", UTF_8);

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

        String8FW string = stringRW.wrap(buffer, 0, limit)
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
    public void shouldFailToWrapInBuilderWithOffsetEqualsMaxLimit()
    {
        stringRW.wrap(buffer, 10, 10);
    }

    @Test
    public void shouldWrapInBuilderWithSufficientLength()
    {
        stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailWrapInBuilderWithOffsetEqualsMaxLimit()
    {
        stringRW.wrap(buffer, 10, 10);
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
    public void shouldReturnNullFromTryWrapWithInsufficientLength()
    {
        buffer.putByte(10,  (byte) 1);
        assertNull(stringRO.tryWrap(buffer, 10, 11));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToWrapWithInsufficientLength()
    {
        buffer.putByte(10,  (byte) 1);
        stringRO.wrap(buffer, 10, 11);
    }

    @Test
    public void shouldTryWrapStringWithLengthZero()
    {
        buffer.putByte(10,  (byte) 0);
        assertEquals(stringRO, stringRO.tryWrap(buffer, 10, 11));
    }

    @Test
    public void shouldWrapStringWithLengthZero()
    {
        buffer.putByte(10,  (byte) 0);
        stringRO.wrap(buffer, 10, 11);
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
    public void shouldFailToSetUsingString8FWWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        try
        {
            stringRW.wrap(buffer, 10, 10 + LENGTH_SIZE)
                .set(asString8FW("1"));
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
                .set(new String8FW((String)null))
                .build()
                .limit();
        assertEquals(1, limit);
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
        assertEquals(1, limit);
        stringRO.wrap(buffer,  0,  limit);
        assertEquals(LENGTH_SIZE, stringRO.limit());
        assertEquals(LENGTH_SIZE, stringRO.sizeof());
        assertNull(stringRO.asString());
        assertNull(stringRO.value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailToBuildLargeString() throws Exception
    {
        String str = String.format("%270s", "0");
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
    public void shouldSetUsingString8FW() throws Exception
    {
        int limit = stringRW.wrap(buffer, 0, 50)
                .set(asString8FW("value1"))
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

    private static MutableDirectBuffer asBuffer(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(value.length()));
        buffer.putStringWithoutLengthUtf8(0, value);
        return buffer;
    }

    private static String8FW asString8FW(
        String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new String8FW.Builder().wrap(buffer, 0, buffer.capacity()).set(value, UTF_8).build();
    }

}
