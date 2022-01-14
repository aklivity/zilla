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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

public final class KafkaChecksum
{
    private KafkaChecksum()
    {
    }

    /**
     * The code below comes from zlib and JCraft's CRC32 implementation
     * (https://github.com/ymnk/jzlib). The most significant change in the zlib code was to use
     * the CRC-32c polynomial (0x82f63b78L), instead of the CRC-32 one (0xedb88320UL).
     *
     * zlib.h -- interface of the 'zlib' general purpose compression library
     * version 1.2.8, April 28th, 2013
     * Copyright (C) 1995-2013 Jean-loup Gailly and Mark Adler
     *
     * Copyright (c) 2000-2011 ymnk, JCraft,Inc. All rights reserved.
     */
    private static final int GF2_DIM = 32;
    public static long combineCRC32C(
        long crc1,
        long crc2,
        long len2)
    {
        long row;
        long[] even = new long[GF2_DIM];
        long[] odd = new long[GF2_DIM];

        // degenerate case (also disallow negative lengths)
        if (len2 <= 0)
        {
            return crc1;
        }

        // put operator for one zero bit in odd
        odd[0] = 0x82F63B78L;          // CRC-32C polynomial
        row = 1;
        for (int n = 1; n < GF2_DIM; n++)
        {
            odd[n] = row;
            row <<= 1;
        }

        // put operator for two zero bits in even
        gfTwoMatrixSquare(even, odd);

        // put operator for four zero bits in odd
        gfTwoMatrixSquare(odd, even);

        // apply len2 zeros to crc1 (first square will put the operator for one
        // zero byte, eight zero bits, in even)
        do
        {
            // apply zeros operator for this bit of len2
            gfTwoMatrixSquare(even, odd);
            if ((len2 & 1) != 0)
            {
                crc1 = gfTwoMatrixTimes(even, crc1);
            }
            len2 >>= 1;

            // if no more bits set, then done
            if (len2 == 0)
            {
                break;
            }

            // another iteration of the loop with odd and even swapped
            gfTwoMatrixSquare(odd, even);
            if ((len2 & 1) != 0)
            {
                crc1 = gfTwoMatrixTimes(odd, crc1);
            }
            len2 >>= 1;

            // if no more bits set, then done
        } while (len2 != 0);

        /* return combined crc */
        crc1 ^= crc2;
        return crc1;
    }

    private static long gfTwoMatrixTimes(
        long[] mat,
        long vec)
    {
        long sum = 0;
        int index = 0;
        while (vec != 0)
        {
            if ((vec & 1) != 0)
            {
                sum ^= mat[index];
            }
            vec >>= 1;
            index++;
        }
        return sum;
    }

    private static void gfTwoMatrixSquare(
        long[] square,
        long[] mat)
    {
        for (int n = 0; n < GF2_DIM; n++)
        {
            square[n] = gfTwoMatrixTimes(mat, mat[n]);
        }
    }
}
