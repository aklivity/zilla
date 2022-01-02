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
import static org.agrona.BufferUtil.NATIVE_BYTE_ORDER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteOrder;
import java.util.Arrays;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.cog.internal.test.types.Flyweight;

public class FlyweightTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(150))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final class TestFlyweight extends Flyweight
    {
        @Override
        public int limit()
        {
            return maxLimit();
        }

        @Override
        public Flyweight wrap(org.agrona.DirectBuffer buffer, int offset, int maxLimit)
        {
            return super.wrap(buffer, offset, maxLimit);
        };
    }

    private TestFlyweight flyweigthRO = new TestFlyweight();

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToWrapWhenOffsetExceedsMaxLimit() throws Exception
    {
        flyweigthRO.wrap(buffer,  4,  1);
    }

    @Test
    public void shouldReturnNullFromTryWrapWhenOffsetExceedsMaxLimit() throws Exception
    {
        assertNull(flyweigthRO.tryWrap(buffer,  4,  1));
    }

    @Test
    public void shouldReturnFalseFromEqualsWithDifferentContent() throws Exception
    {
        buffer.putStringWithoutLengthUtf8(0, "asdf");
        buffer.putStringWithoutLengthUtf8(10, "qwer");
        Flyweight zis = new TestFlyweight().wrap(buffer,  0,  4);
        Flyweight zat = new TestFlyweight().wrap(buffer,  10,  14);
        assertFalse(zis.equals(zat));
    }

    @Test
    public void shouldReturnFalseFromEqualsWithDifferentLength() throws Exception
    {
        buffer.putStringWithoutLengthUtf8(0, "asdf");
        buffer.putStringWithoutLengthUtf8(10, "asdfg");
        Flyweight zis = new TestFlyweight().wrap(buffer,  0,  4);
        Flyweight zat = new TestFlyweight().wrap(buffer,  10,  15);
        assertFalse(zis.equals(zat));
    }

    @Test
    public void shouldReturnFalseFromEqualsWithNull() throws Exception
    {
        buffer.putStringWithoutLengthUtf8(0, "asdf");
        Flyweight zis = new TestFlyweight().wrap(buffer,  0,  4);
        assertFalse(zis.equals(null));
    }

    @Test
    public void shouldReturnTrueFromEquals() throws Exception
    {
        buffer.putStringWithoutLengthUtf8(0, "asdf");
        buffer.putStringWithoutLengthUtf8(10, "asdf");
        Flyweight zis = new TestFlyweight().wrap(buffer,  0,  4);
        Flyweight zat = new TestFlyweight().wrap(buffer,  10,  14);
        assertTrue(zis.equals(zat));
    }

    @Test
    public void shouldReturnHashCode() throws Exception
    {
        buffer.putStringWithoutLengthUtf8(0, "asdf");
        Flyweight flyweight = new TestFlyweight().wrap(buffer,  0,  4);
        assertEquals(Arrays.hashCode("asdf".getBytes()), flyweight.hashCode());
    }

    public static void putMediumInt(
        MutableDirectBuffer buffer,
        int index,
        int value)
    {
        final byte byte0 = (byte) (value & 0xff);
        final byte byte1 = (byte) ((value >> 8) & 0xff);
        final byte byte2 = (byte) ((value >> 16) & 0xff);
        final byte byte3 = (byte) ((value >> 24) & 0xff);

        assert byte3 == 0 || byte3 == -1;

        if (NATIVE_BYTE_ORDER == ByteOrder.BIG_ENDIAN)
        {
            buffer.putByte(index + 2, byte0);
            buffer.putByte(index + 1, byte1);
            buffer.putByte(index, byte2);
        }
        else if (NATIVE_BYTE_ORDER == ByteOrder.LITTLE_ENDIAN)
        {
            buffer.putByte(index + 2, byte2);
            buffer.putByte(index + 1, byte1);
            buffer.putByte(index, byte0);
        }
        else
        {
            assert false : "unexpected native byte order";
        }
    }
}
