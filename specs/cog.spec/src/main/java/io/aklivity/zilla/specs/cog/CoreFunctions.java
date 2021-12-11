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
package io.aklivity.zilla.specs.cog;

import static java.lang.Long.highestOneBit;
import static java.lang.Long.numberOfTrailingZeros;
import static java.lang.ThreadLocal.withInitial;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.cog.internal.types.String16FW;
import io.aklivity.zilla.specs.cog.internal.types.String8FW;
import io.aklivity.zilla.specs.cog.internal.types.stream.Capability;

public final class CoreFunctions
{
    private static final ThreadLocal<String8FW.Builder> STRING_RW = withInitial(String8FW.Builder::new);
    private static final ThreadLocal<String16FW.Builder> STRING16_RW = withInitial(String16FW.Builder::new);
    private static final ThreadLocal<String16FW.Builder> STRING16N_RW = withInitial(() -> new String16FW.Builder(BIG_ENDIAN));

    @Function
    public static byte[] fromHex(
        String text)
    {
        return BitUtil.fromHex(text);
    }

    @Function
    public static Random random()
    {
        return ThreadLocalRandom.current();
    }

    @Function
    public static byte[] string(
        String text)
    {
        int capacity = SIZE_OF_BYTE + ofNullable(text).orElse("").length() * 2 + 1;
        MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[capacity]);
        String8FW string = STRING_RW.get()
                                   .wrap(writeBuffer, 0, writeBuffer.capacity())
                                   .set(text, UTF_8)
                                   .build();

        final byte[] array = new byte[string.sizeof()];
        string.buffer().getBytes(0, array);
        return array;
    }

    @Function
    public static byte[] string16(
        String text)
    {
        int capacity = SIZE_OF_SHORT + ofNullable(text).orElse("").length() * 2 + 1;
        MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[capacity]);
        String16FW string16 = STRING16_RW.get()
                                         .wrap(writeBuffer, 0, writeBuffer.capacity())
                                         .set(text, UTF_8)
                                         .build();

        final byte[] array = new byte[string16.sizeof()];
        string16.buffer().getBytes(0, array);
        return array;
    }

    @Function
    public static byte[] string16n(
        String text)
    {
        int capacity = SIZE_OF_SHORT + ofNullable(text).orElse("").length() * 2 + 1;
        MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[capacity]);
        String16FW string16 = STRING16N_RW.get()
                                          .wrap(writeBuffer, 0, writeBuffer.capacity())
                                          .set(text, UTF_8)
                                          .build();

        final byte[] array = new byte[string16.sizeof()];
        string16.buffer().getBytes(0, array);
        return array;
    }

    @Function
    public static byte[] varstring(
        String text)
    {
        byte[] bytes = text != null ? text.getBytes(UTF_8) : null;
        return varbytes(bytes);
    }

    @Function
    public static byte[] varbytes(
        byte[] bytes)
    {
        if (bytes == null)
        {
            return varuintn(-1L);
        }
        else
        {
            byte[] length = varuintn(bytes.length);

            byte[] varbytes = new byte[length.length + bytes.length];
            System.arraycopy(length, 0, varbytes, 0, length.length);
            System.arraycopy(bytes, 0, varbytes, length.length, bytes.length);

            return varbytes;
        }
    }

    @Function
    public static byte[] varuintn(
        long nvalue)
    {
        return varuint(nvalue + 1);
    }

    @Function
    public static byte[] varuint(
        long value)
    {
        return varbits(value);
    }

    @Function
    public static byte[] varint(
        long value)
    {
        final long bits = (value << 1) ^ (value >> 63);

        return varbits(bits);
    }

    private static byte[] varbits(
        long bits)
    {
        switch (bits != 0L ? (int) Math.ceil((1 + numberOfTrailingZeros(highestOneBit(bits))) / 7.0) : 1)
        {
        case 1:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f)
            };
        case 2:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f)
            };
        case 3:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f)
            };
        case 4:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f)
            };
        case 5:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f)
            };
        case 6:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f)
            };
        case 7:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f)
            };
        case 8:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f),
            };
        case 9:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f | 0x80),
                (byte) ((bits >> 56) & 0x7f),
            };
        default:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f | 0x80),
                (byte) ((bits >> 56) & 0x7f | 0x80),
                (byte) ((bits >> 63) & 0x01)
            };
        }
    }

    @Function
    public static byte capabilities(
        String capability,
        String... optionalCapabilities)
    {
        return of(capability, optionalCapabilities);
    }

    private static byte of(
        String name,
        String... optionalNames)
    {
        byte capabilityMask = 0x00;
        capabilityMask |= 1 << Capability.valueOf(name).ordinal();
        for (int i = 0; i < optionalNames.length; i++)
        {
            final int capabilityOrdinal = Capability.valueOf(optionalNames[i]).ordinal();
            assert capabilityOrdinal < Byte.SIZE;
            capabilityMask |= 1 << capabilityOrdinal;
        }
        return capabilityMask;
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(CoreFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "core";
        }
    }

    private CoreFunctions()
    {
        // utility
    }
}
