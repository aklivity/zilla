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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public enum StringValidator
{
    UTF_8
    {
        public boolean validate(
            byte[] byteArray)
        {
            int i = 0;
            while (i < byteArray.length)
            {
                int numBytes;
                if ((byteArray[i] & 0b10000000) == 0b00000000)
                {
                    numBytes = 1;
                }
                else if ((byteArray[i] & 0b11100000) == 0b11000000)
                {
                    numBytes = 2;
                }
                else if ((byteArray[i] & 0b11110000) == 0b11100000)
                {
                    numBytes = 3;
                }
                else if ((byteArray[i] & 0b11111000) == 0b11110000)
                {
                    numBytes = 4;
                }
                else
                {
                    return false;
                }

                for (int j = 1; j < numBytes; j++)
                {
                    if (i + j >= byteArray.length)
                    {
                        return false;
                    }
                    if ((byteArray[i + j] & 0b11000000) != 0b10000000)
                    {
                        return false;
                    }
                }
                i += numBytes;
            }
            return true;
        }
    },
    UTF_16
    {
        public boolean validate(
            byte[] byteArray)
        {
            int i = 0;
            boolean status = false;

            while (i < byteArray.length)
            {
                if (i + 1 >= byteArray.length)
                {
                    status = false;
                    break;
                }

                int highByte = byteArray[i] & 0xFF;
                int lowByte = byteArray[i + 1] & 0xFF;
                int codeUnit = (highByte << 8) | lowByte;

                if (codeUnit >= 0xD800 && codeUnit <= 0xDBFF)
                {
                    if (i + 3 >= byteArray.length)
                    {
                        status = false;
                        break;
                    }
                    int secondHighByte = byteArray[i + 2] & 0xFF;
                    int secondLowByte = byteArray[i + 3] & 0xFF;
                    int secondCodeUnit = (secondHighByte << 8) | secondLowByte;
                    if (secondCodeUnit < 0xDC00 || secondCodeUnit > 0xDFFF)
                    {
                        status = false;
                        break;
                    }
                    i += 4;
                }
                else if (codeUnit >= 0xDC00 && codeUnit <= 0xDFFF)
                {
                    status = false;
                    break;
                }
                else
                {
                    i += 2;
                }
                status = true;
            }
            return status;
        }
    };

    public abstract boolean validate(byte[] byteArray);

    static final Predicate<byte[]> INVALID_ENCODING = b -> false;
    static final Map<String, Predicate<byte[]>> ENCODING_VALIDATORS = new HashMap<>();
    static
    {
        ENCODING_VALIDATORS.put("utf_8", UTF_8::validate);
        ENCODING_VALIDATORS.put("utf_16", UTF_16::validate);
    }
}
