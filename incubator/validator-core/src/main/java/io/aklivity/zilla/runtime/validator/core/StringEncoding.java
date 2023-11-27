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
package io.aklivity.zilla.runtime.validator.core;

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
            int bytesIndex = 0;
            while (bytesIndex < length)
            {
                int numBytes;
                if ((data.getByte(bytesIndex) & 0b10000000) == 0b00000000)
                {
                    numBytes = 1;
                }
                else if ((data.getByte(bytesIndex) & 0b11100000) == 0b11000000)
                {
                    numBytes = 2;
                }
                else if ((data.getByte(bytesIndex) & 0b11110000) == 0b11100000)
                {
                    numBytes = 3;
                }
                else if ((data.getByte(bytesIndex) & 0b11111000) == 0b11110000)
                {
                    numBytes = 4;
                }
                else
                {
                    break;
                }

                for (int j = 1; j < numBytes; j++)
                {
                    if (bytesIndex + j >= length || (data.getByte(bytesIndex + j) & 0b11000000) != 0b10000000)
                    {
                        break;
                    }
                }
                bytesIndex += numBytes;
            }
            return bytesIndex == length;
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
            int bytesIndex = 0;

            while (bytesIndex < length)
            {
                if (bytesIndex == length - 1)
                {
                    break;
                }

                int highByte = data.getByte(bytesIndex) & 0xFF;
                int lowByte = data.getByte(bytesIndex + 1) & 0xFF;
                int codeUnit = (highByte << 8) | lowByte;

                if (codeUnit >= 0xD800 && codeUnit <= 0xDBFF)
                {
                    if (bytesIndex + 3 >= length)
                    {
                        break;
                    }
                    int secondHighByte = data.getByte(bytesIndex + 2) & 0xFF;
                    int secondLowByte = data.getByte(bytesIndex + 3) & 0xFF;
                    int secondCodeUnit = (secondHighByte << 8) | secondLowByte;
                    if (secondCodeUnit < 0xDC00 || secondCodeUnit > 0xDFFF)
                    {
                        break;
                    }
                    bytesIndex += 4;
                }
                else if (codeUnit >= 0xDC00 && codeUnit <= 0xDFFF)
                {
                    break;
                }
                else
                {
                    bytesIndex += 2;
                }
            }
            return bytesIndex == length;
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

    static StringEncoding of(
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
