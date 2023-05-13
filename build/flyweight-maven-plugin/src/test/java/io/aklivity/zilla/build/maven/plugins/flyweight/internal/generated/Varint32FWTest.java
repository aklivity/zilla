/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.Varint32FW;

public class Varint32FWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final MutableDirectBuffer expected = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final Varint32FW.Builder varint32RW = new Varint32FW.Builder();
    private final Varint32FW varint32RO = new Varint32FW();

    @Test
    public void shouldNotTryWrapZeroLengthBuffer() throws Exception
    {
        assertNull(varint32RO.tryWrap(buffer,  10,  10));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapZeroLengthBuffer() throws Exception
    {
        varint32RO.wrap(buffer,  10,  10);
    }

    @Test
    public void shouldNotTryWrapIncompleteValue() throws Exception
    {
        // Set up buffer so it will give index out of bounds if the implementation attempts to compute
        // the varint value without respecting maxLimit
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[2]);
        buffer.putByte(0, (byte) 0x81);
        buffer.putByte(1, (byte) 0x81);
        assertNull(varint32RO.tryWrap(buffer, 0, buffer.capacity()));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapIncompleteValue() throws Exception
    {
        // Set up buffer so it will overflow if the implementation attempts to compute
        // the varint value without respecting maxLimit
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[2]);
        buffer.putByte(0, (byte) 0x81);
        buffer.putByte(1, (byte) 0x81);
        varint32RO.wrap(buffer, 0, 1);
    }

    @Test
    public void shouldNotTryWrapValueWith33bits() throws Exception
    {
        buffer.putByte(50, (byte) 0xfe);
        buffer.putByte(51, (byte) 0xff);
        buffer.putByte(52, (byte) 0xff);
        buffer.putByte(53, (byte) 0xff);
        buffer.putByte(54, (byte) 0x1f);
        assertNull(varint32RO.tryWrap(buffer,  50,  buffer.capacity()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotWrapValueWith33bits() throws Exception
    {
        buffer.putByte(50, (byte) 0xfe);
        buffer.putByte(51, (byte) 0xff);
        buffer.putByte(52, (byte) 0xff);
        buffer.putByte(53, (byte) 0xff);
        buffer.putByte(54, (byte) 0x1f);
        varint32RO.wrap(buffer,  50,  buffer.capacity());
        assertEquals(Integer.MAX_VALUE, varint32RO.value());
    }

    @Test
    public void shouldTryWrap() throws Exception
    {
        // Actual value is -66, zigzagged value is 132-1 = 131 = 0x83
        buffer.putByte(50, (byte) 0x83);
        buffer.putByte(51, (byte) 0x01);
        Varint32FW result = varint32RO.tryWrap(buffer, 50, 52);
        assertEquals(-66, result.value());
    }

    @Test
    public void shouldWrap() throws Exception
    {
        // Actual value is -66, zigzagged value is 132-1 = 131 = 0x83
        buffer.putByte(50, (byte) 0x83);
        buffer.putByte(51, (byte) 0x01);
        varint32RO.wrap(buffer, 50, 52);
    }

    @Test
    public void shouldReadOneByteValue() throws Exception
    {
        int offset = 13;
        buffer.putByte(offset,  (byte) 0x18);
        assertEquals(offset + 1, varint32RO.wrap(buffer,  offset,  21).limit());
        assertEquals(12, varint32RO.value());
    }

    @Test
    public void shouldReadTwoByteValue() throws Exception
    {
        // Actual value is -66, zigzagged value is 132-1 = 131 = 0x83
        buffer.putByte(50, (byte) 0x83);
        buffer.putByte(51, (byte) 0x01);
        assertEquals(52, varint32RO.wrap(buffer,  50,  buffer.capacity()).limit());
        assertEquals(-66, varint32RO.value());
    }

    @Test
    public void shouldReadMostPositiveValue() throws Exception
    {
        // Actual value is Integer.MAX_VALUE = 0x7fffffff (31 bits set)
        // Zig-zagged value is 0xfffffffe
        // 7-bit values are 0f 7f 7f 7f 7e (which must be reversed)
        buffer.putByte(50, (byte) 0xfe);
        buffer.putByte(51, (byte) 0xff);
        buffer.putByte(52, (byte) 0xff);
        buffer.putByte(53, (byte) 0xff);
        buffer.putByte(54, (byte) 0x0f);
        assertEquals(55, varint32RO.wrap(buffer,  50,  buffer.capacity()).limit());
        assertEquals(Integer.MAX_VALUE, varint32RO.value());
    }

    @Test
    public void shouldReadMostNegativeValue() throws Exception
    {
        // Actual value is Integer.MIN_VALUE = -2147483648
        // Zig-zagged value is 0xffffffff
        // 7-bit values are 0f 7f 7f 7f 7e (which must be reversed)
        buffer.putByte(50, (byte) 0xff);
        buffer.putByte(51, (byte) 0xff);
        buffer.putByte(52, (byte) 0xff);
        buffer.putByte(53, (byte) 0xff);
        buffer.putByte(54, (byte) 0x0f);
        varint32RO.wrap(buffer,  50,  buffer.capacity());
        assertEquals(Integer.MIN_VALUE, varint32RO.value());
    }

    @Test
    public void shouldSetMostPositiveValue() throws Exception
    {
        // Actual value is Integer.MAX_VALUE = 0x7fffffff (31 bits set)
        // Zig-zagged value is 0xfffffffe
        // 7-bit values are 0f 7f 7f 7f 7e (which must be reversed)
        expected.putByte(50, (byte) 0xfe);
        expected.putByte(51, (byte) 0xff);
        expected.putByte(52, (byte) 0xff);
        expected.putByte(53, (byte) 0xff);
        expected.putByte(54, (byte) 0x0f);
        varint32RW.wrap(buffer,  50,  buffer.capacity())
            .set(Integer.MAX_VALUE)
            .build();
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetMostNegativeValue() throws Exception
    {
        // Actual value is Integer.MIN_VALUE = -2147483648
        // Zig-zagged value is 0xffffffff
        // 7-bit values are 7f 7f 7f 7f 7e (which must be reversed)
        expected.putByte(50, (byte) 0xff);
        expected.putByte(51, (byte) 0xff);
        expected.putByte(52, (byte) 0xff);
        expected.putByte(53, (byte) 0xff);
        expected.putByte(54, (byte) 0x0f);
        varint32RW.wrap(buffer,  50,  buffer.capacity())
            .set(Integer.MIN_VALUE)
            .build();
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetZeroValue() throws Exception
    {
        expected.putByte(10, (byte) 0x00);
        varint32RW.wrap(buffer, 10, 11)
            .set(0)
            .build();
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetOneByteValue() throws Exception
    {
        expected.putByte(10, (byte) 0x18);
        varint32RW.wrap(buffer, 10, 21)
            .set(12)
            .build();
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldSetTwoByteValue() throws Exception
    {
        // Actual value is -66, zigzagged value is 132-1 = 131 = 0x83
        expected.putByte(0, (byte) 0x83);
        expected.putByte(1, (byte) 0x01);
        varint32RW.wrap(buffer, 0, buffer.capacity())
            .set(-66)
            .build();
        assertEquals(expected.byteBuffer(), buffer.byteBuffer());
    }

    @Test
    public void shouldReportAsString() throws Exception
    {
        // Actual value is Integer.MAX_VALUE = 0x7ffffffe (31 bits set)
        // Zig-zagged value is 0xfffffffe
        // 7-bit values are 3f 7f 7f 7f 7f (which must be reversed)
        buffer.putByte(50, (byte) 0xfe);
        buffer.putByte(51, (byte) 0xff);
        buffer.putByte(52, (byte) 0xff);
        buffer.putByte(53, (byte) 0xff);
        buffer.putByte(54, (byte) 0x0f);
        varint32RO.wrap(buffer,  50,  buffer.capacity());
        assertEquals(Integer.toString(Integer.MAX_VALUE), varint32RO.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotBuildWithZeroLengthBuffer() throws Exception
    {
        expected.putByte(10, (byte) 0x18);
        varint32RW.wrap(buffer, 10, 10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotSetValueWithInsufficientSpace() throws Exception
    {
        expected.putByte(10, (byte) 0x18);
        varint32RW.wrap(buffer, 10, 11)
            .set(70);
    }
}
