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
package io.aklivity.zilla.runtime.binding.http.internal.hpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;

/*

   Flyweight for HPACK String Literal Representation

   For example, for n = 5

     0   1   2   3   4   5   6   7
   +---+---+---+---+---+---+---+---+
   | ? | ? | ? | 1   1   1   1   1 |
   +---+---+---+-------------------+
   | 1 |    Value-(2^N-1) LSB      |
   +---+---------------------------+
                  ...
   +---+---------------------------+
   | 0 |    Value-(2^N-1) MSB      |
   +---+---------------------------+

 */
public class HpackIntegerFW extends Flyweight
{
    private static final int[] TWON_TABLE;
    private final int n;

    private int decodedOctets;
    private int value;

    static
    {
        TWON_TABLE = new int[32];
        for (int i = 0; i < 32; ++i)
        {
            TWON_TABLE[i] = 1 << i;
        }
    }

    public HpackIntegerFW(int n)
    {
        this.n = n;
    }

    public int integer()
    {
        return value;
    }

    public boolean error()
    {
        return offset() + decodedOctets > maxLimit();
    }

    @Override
    public int limit()
    {
        return error() ? maxLimit() : offset() + decodedOctets;
    }

    @Override
    public HpackIntegerFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        decodedOctets = 0;
        value = 0;
        super.wrap(buffer, offset, maxLimit);

        /*
         * decode I from the next N bits
         * if I < 2^N - 1, return I
         * else
         *     M = 0
         *     repeat
         *         B = next octet
         *         I = I + (B & 127) * 2^M
         *         M = M + 7
         *     while B & 128 == 128
         * return I
         */
        int i = (TWON_TABLE[n] - 1) & buffer().getByte(offset());
        decodedOctets++;
        if (i >= TWON_TABLE[n] - 1)
        {
            int m = 0;
            int b;
            do
            {
                b = buffer().getByte(offset() + decodedOctets);
                decodedOctets++;
                i = i + (b & 127) * TWON_TABLE[m];
                m += 7;
            } while ((b & 128) == 128);
        }
        value = i;

        checkLimit(limit(), maxLimit);

        return this;
    }

    public static final class Builder extends Flyweight.Builder<HpackIntegerFW>
    {
        private final int n;

        public Builder(int n)
        {
            super(new HpackIntegerFW(n));
            this.n = n;
        }

        @Override
        public HpackIntegerFW.Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);
            return this;
        }

        /*
         * Encodes integer in HPACK representation
         *
         *  if I < 2^N - 1, encode I on N bits
         *  else
         *      encode (2^N - 1) on N bits
         *      I = I - (2^N - 1)
         *      while I >= 128
         *          encode (I % 128 + 128) on 8 bits
         *          I = I / 128
         *      encode I on 8 bits
         *
         * @param offset offset for current octet
         * @param n number of bits of the prefix
         */
        public HpackIntegerFW.Builder integer(int value)
        {
            assert n >= 1;
            assert n <= 8;
            int twoNminus1 = TWON_TABLE[n] - 1;
            int i = offset();

            byte cur = buffer().getByte(i);
            if (value < twoNminus1)
            {
                buffer().putByte(i++, (byte) (cur | value));
            }
            else
            {
                buffer().putByte(i++, (byte) (cur | twoNminus1));
                int remaining = value - twoNminus1;
                while (remaining >= 128)
                {
                    buffer().putByte(i++, (byte) (remaining % 128 + 128));
                    remaining = remaining / 128;
                }
                buffer().putByte(i++, (byte) remaining);
            }

            limit(i);

            return this;
        }

    }

}

