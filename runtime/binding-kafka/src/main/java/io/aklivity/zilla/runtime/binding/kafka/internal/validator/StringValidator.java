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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.StringValidatorConfig;

public final class StringValidator implements Validator
{
    private String encoding;

    public StringValidator(
        StringValidatorConfig config)
    {
        this.encoding = config.encoding;
    }

    @Override
    public boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        boolean valid = true;
        if (data != null)
        {
            byte[] payloadBytes = new byte[length];
            data.getBytes(0, payloadBytes);
            switch (encoding)
            {
            case "utf_8":
                valid = isValidUTF8(payloadBytes);
            }
        }
        return valid;
    }

    private boolean isValidUTF8(
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
}
