/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import org.agrona.DirectBuffer;

public final class AguiA2aKeyHash
{
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long FMIX_MULTIPLIER_1 = 0xff51afd7ed558ccdL;
    private static final long FMIX_MULTIPLIER_2 = 0xc4ceb9fe1a85ec53L;

    private static final int FIELD_SEPARATOR = 0xFF;
    private static final int ABSENT_IDENTITY = 0xFE;

    public static long hash(
        String identity,
        String agent,
        String threadId)
    {
        long hash = hashIdentity(FNV_OFFSET_BASIS, identity);
        hash = hashUtf8(hash, agent);
        hash = hashByte(hash, FIELD_SEPARATOR);
        hash = hashUtf8(hash, threadId);
        hash = hashByte(hash, FIELD_SEPARATOR);

        return finalize(hash);
    }

    public static long hash(
        String identity,
        String agent,
        DirectBuffer threadId,
        int offset,
        int length)
    {
        long hash = hashIdentity(FNV_OFFSET_BASIS, identity);
        hash = hashUtf8(hash, agent);
        hash = hashByte(hash, FIELD_SEPARATOR);
        for (int index = offset; index < offset + length; index++)
        {
            hash = hashByte(hash, threadId.getByte(index));
        }
        hash = hashByte(hash, FIELD_SEPARATOR);

        return finalize(hash);
    }

    private static long hashIdentity(
        long hash,
        String identity)
    {
        long result = hash;

        if (identity != null)
        {
            result = hashUtf8(result, identity);
        }
        else
        {
            result = hashByte(result, ABSENT_IDENTITY);
        }

        return hashByte(result, FIELD_SEPARATOR);
    }

    private static long hashUtf8(
        long hash,
        String value)
    {
        long result = hash;

        if (value != null)
        {
            final int length = value.length();
            for (int index = 0; index < length; index++)
            {
                int codePoint = value.charAt(index);

                if (Character.isHighSurrogate((char) codePoint) &&
                    index + 1 < length &&
                    Character.isLowSurrogate(value.charAt(index + 1)))
                {
                    codePoint = Character.toCodePoint((char) codePoint, value.charAt(++index));
                }
                else if (Character.isSurrogate((char) codePoint))
                {
                    codePoint = '?';
                }

                result = hashCodePoint(result, codePoint);
            }
        }

        return result;
    }

    private static long hashCodePoint(
        long hash,
        int codePoint)
    {
        long result = hash;

        if (codePoint < 0x80)
        {
            result = hashByte(result, codePoint);
        }
        else if (codePoint < 0x800)
        {
            result = hashByte(result, 0xC0 | codePoint >> 6);
            result = hashByte(result, 0x80 | codePoint & 0x3F);
        }
        else if (codePoint < 0x10000)
        {
            result = hashByte(result, 0xE0 | codePoint >> 12);
            result = hashByte(result, 0x80 | codePoint >> 6 & 0x3F);
            result = hashByte(result, 0x80 | codePoint & 0x3F);
        }
        else
        {
            result = hashByte(result, 0xF0 | codePoint >> 18);
            result = hashByte(result, 0x80 | codePoint >> 12 & 0x3F);
            result = hashByte(result, 0x80 | codePoint >> 6 & 0x3F);
            result = hashByte(result, 0x80 | codePoint & 0x3F);
        }

        return result;
    }

    private static long hashByte(
        long hash,
        int value)
    {
        return (hash ^ (value & 0xFF)) * FNV_PRIME;
    }

    private static long finalize(
        long hash)
    {
        long result = hash;
        result ^= result >>> 33;
        result *= FMIX_MULTIPLIER_1;
        result ^= result >>> 33;
        result *= FMIX_MULTIPLIER_2;
        result ^= result >>> 33;
        return result;
    }

    private AguiA2aKeyHash()
    {
    }
}
