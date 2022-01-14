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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generated;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.test.types.OctetsFW;

public class OctetsFWTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(100))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();
    private final OctetsFW octetsRO = new OctetsFW();

    @Test
    public void shouldDefaultToEmpty() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(0, octetsRO.sizeof());

    }

    @Test
    public void shouldTryWrap()
    {
        byte value = 1;
        buffer.putByte(10, value);
        OctetsFW octets = octetsRO.tryWrap(buffer, 10, 11);
        byte[] result = new byte[1];
        octets.get((b, o, l) -> result[0] = b.getByte(o));
        assertEquals(value, result[0]);
    }

    @Test
    public void shouldTryWrapEmptyAtNonZeroOffet()
    {
        OctetsFW octets = octetsRO.tryWrap(buffer, 10, 10);

        assertNotNull(octets);
        assertEquals(0, octets.sizeof());
        assertEquals(0, octets.value().capacity());
    }

    @Test
    public void shouldWrap()
    {
        byte value = 1;
        buffer.putByte(10, value);
        OctetsFW octets = octetsRO.wrap(buffer, 10, 11);
        byte[] result = new byte[1];
        octets.get((b, o, l) -> result[0] = b.getByte(o));
        assertEquals(value, result[0]);
        assertEquals(value, octets.value().getByte(0));
    }

    @Test
    public void shouldWrapEmptyAtNonZeroOffet()
    {
        OctetsFW octets = octetsRO.wrap(buffer, 10, 10);

        assertEquals(0, octets.sizeof());
        assertEquals(0, octets.value().capacity());
    }

    @Test
    public void shouldNotTryWrapWithMaxValueLTOffset()
    {
        assertNull(octetsRO.tryWrap(buffer, 10, 9));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldNotWrapWithMaxValueLTOffset()
    {
        octetsRO.wrap(buffer, 10, 9);
    }

    @Test
    public void shouldCreateWithZeroLength()
    {
        octetsRW.wrap(buffer, 10, 10);
        octetsRO.wrap(buffer,  10, 10);
        assertEquals(0, octetsRO.sizeof());    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingMutatorWhenExceedsMaxLimit()
    {
        try
        {
            octetsRW.wrap(buffer, 10, 11)
                .set((b, o, l) ->
                {
                    b.putBytes(o, "12".getBytes(UTF_8));
                    return 2;
                });
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Memory does get written beyond maxLimit in this case because the visitor violated the contract
            assertNotEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingOctetsFWWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        try
        {
            octetsRW.wrap(buffer, 10, 11)
                .set(asOctetsFW("12"));
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToSetUsingBufferWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        try
        {
            octetsRW.wrap(buffer, 10, 11)
                .set(asBuffer("12"), 0, 2);
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test
    public void shouldSetUsingOctetsFW() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .set(asOctetsFW("value1"))
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldSetUsingBuffer() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .set(asBuffer("value1"), 0, "value1".length())
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldSetUsingByteArray() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .set("value1".getBytes(UTF_8))
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldSetUsingVisitor() throws Exception
    {
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToPutUsingMutatorWhenExceedsMaxLimit()
    {
        octetsRW.wrap(buffer, 10, 11)
            .set((b, o, l) ->
            {
                b.putBytes(o, "1".getBytes(UTF_8));
                return 1;
            });
        try
        {
            octetsRW.set((b, o, l) ->
            {
                b.putBytes(o, "2".getBytes(UTF_8));
                return 2;
            });
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Memory does get written beyond maxLimit in this case because the visitor violated the contract
            assertNotEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToPutUsingOctetsFWWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        octetsRW.wrap(buffer, 10, 11)
                .put(asOctetsFW("1"));
        try
        {
            octetsRW.put(asOctetsFW("2"));
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldFailToPutUsingBufferWhenExceedsMaxLimit()
    {
        buffer.setMemory(0,  buffer.capacity(), (byte) 0x00);
        octetsRW.wrap(buffer, 10, 11)
                .put(asBuffer("1"), 0, 1);
        try
        {
            octetsRW.put(asBuffer("2"), 0, 1);
        }
        finally
        {
            byte[] bytes = new byte[2];
            buffer.getBytes(10, bytes);
            // Make sure memory was not written beyond maxLimit
            assertEquals("Buffer shows memory was written beyond maxLimit: " + BitUtil.toHex(bytes),
                         0, buffer.getByte(11));
        }
    }

    @Test
    public void shouldPutUsingOctetsFW() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .put(asOctetsFW("val"))
                .put(asOctetsFW("ue1"))
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldPutUsingBuffer() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .put(asBuffer("val"), 0, "val".length())
                .put(asBuffer("ue1"), 0, "ue1".length())
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldPutUsingByteArray() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .put("val".getBytes(UTF_8))
                .put("ue1".getBytes(UTF_8))
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));
    }

    @Test
    public void shouldPutUsingVisitor() throws Exception
    {
        int limit = octetsRW.wrap(buffer, 0, buffer.capacity())
                .put((b, o, l) ->
                {
                    b.putBytes(o, "val".getBytes(UTF_8));
                    return 3;
                })
                .put((b, o, l) ->
                {
                    b.putBytes(o, "ue1".getBytes(UTF_8));
                    return 3;
                })
                .build()
                .limit();
        octetsRO.wrap(buffer,  0,  limit);
        assertEquals(6, octetsRO.sizeof());
        assertEquals("value1", asString(octetsRO));    }

    private static MutableDirectBuffer asBuffer(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(value.length()));
        buffer.putStringWithoutLengthUtf8(0, value);
        return buffer;
    }

    private static OctetsFW asOctetsFW(String value)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirect(Byte.SIZE + value.length()));
        return new OctetsFW.Builder().wrap(buffer, 0, buffer.capacity()).set(value.getBytes(UTF_8)).build();
    }

    private String asString(OctetsFW octets)
    {
        byte[] bytes = new byte[octets.sizeof()];
        octets.buffer().getBytes(octets.offset(), bytes);
        return new String(bytes, UTF_8);
    }
}
