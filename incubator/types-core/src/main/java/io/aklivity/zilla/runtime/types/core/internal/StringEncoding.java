/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.types.core.internal;

import org.agrona.DirectBuffer;

public enum StringEncoding
{
    UTF_8
    {
        @Override
        public boolean validate(
            DirectBuffer data,
            int index,
            int length)
        {
            final int limit = index + length;
            validate:
            while (index < limit)
            {
                final int charByte0 = data.getByte(index);
                final int charByteCount = (charByte0 & 0b1000_0000) != 0
                    ? Integer.numberOfLeadingZeros((~charByte0 & 0xff) << 24)
                    : 1;

                final int charByteLimit = index + charByteCount;
                for (int charByteIndex = index + 1; charByteIndex < charByteLimit; charByteIndex++)
                {
                    if (charByteIndex >= limit || (data.getByte(charByteIndex) & 0b11000000) != 0b10000000)
                    {
                        break validate;
                    }
                }
                index += charByteCount;
            }
            return index == limit;
        }
    },

    UTF_16
    {
        @Override
        public boolean validate(
            DirectBuffer data,
            int index,
            int length)
        {
            final int limit = index + length;

            while (index < limit)
            {
                if (index == limit - 1)
                {
                    break;
                }

                int highByte = data.getByte(index) & 0xFF;
                int lowByte = data.getByte(index + 1) & 0xFF;
                int codeUnit = (highByte << 8) | lowByte;

                if (codeUnit >= 0xD800 && codeUnit <= 0xDBFF)
                {
                    if (index + 3 >= limit)
                    {
                        break;
                    }
                    int secondHighByte = data.getByte(index + 2) & 0xFF;
                    int secondLowByte = data.getByte(index + 3) & 0xFF;
                    int secondCodeUnit = (secondHighByte << 8) | secondLowByte;
                    if (secondCodeUnit < 0xDC00 || secondCodeUnit > 0xDFFF)
                    {
                        break;
                    }
                    index += 4;
                }
                else if (codeUnit >= 0xDC00 && codeUnit <= 0xDFFF)
                {
                    break;
                }
                else
                {
                    index += 2;
                }
            }
            return index == limit;
        }
    },

    INVALID
    {
        @Override
        public boolean validate(
            DirectBuffer data,
            int index,
            int length)
        {
            return false;
        }
    };

    public abstract boolean validate(
        DirectBuffer data,
        int index,
        int length);

    public static StringEncoding of(
        String encoding)
    {
        switch (encoding)
        {
        case "utf_8":
            return UTF_8;
        case "utf_16":
            return UTF_16;
        default:
            return INVALID;
        }
    }
}
