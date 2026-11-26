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
    private static final long CRC32C_POLYNOMIAL = 0x82F63B78L;
    private static final long[][] ZERO_BYTES_OPERATORS = newZeroBytesOperators();

    public static long combineCRC32C(
        long crc1,
        long crc2,
        long len2)
    {
        long combined = crc1;

        if (len2 > 0)
        {
            long remaining = len2;
            int power = 0;
            while (remaining != 0)
            {
                if ((remaining & 1) != 0)
                {
                    combined = gfTwoMatrixTimes(ZERO_BYTES_OPERATORS[power], combined);
                }
                remaining >>>= 1;
                power++;
            }

            combined ^= crc2;
        }

        return combined;
    }

    private static long[][] newZeroBytesOperators()
    {
        final long[][] operators = new long[Long.SIZE - 1][];

        long[] operator = new long[GF2_DIM];
        operator[0] = CRC32C_POLYNOMIAL;
        long row = 1;
        for (int n = 1; n < GF2_DIM; n++)
        {
            operator[n] = row;
            row <<= 1;
        }

        // one zero bit squared three times is one zero byte
        for (int squares = 0; squares < 3; squares++)
        {
            operator = newSquare(operator);
        }

        for (int power = 0; power < operators.length; power++)
        {
            operators[power] = operator;
            operator = newSquare(operator);
        }

        return operators;
    }

    private static long[] newSquare(
        long[] mat)
    {
        final long[] square = new long[GF2_DIM];
        gfTwoMatrixSquare(square, mat);
        return square;
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
