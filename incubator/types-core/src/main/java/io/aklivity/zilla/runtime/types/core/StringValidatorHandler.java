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
package io.aklivity.zilla.runtime.types.core;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;
import io.aklivity.zilla.runtime.engine.validator.ValidatorHandler;
import io.aklivity.zilla.runtime.types.core.config.StringValidatorConfig;

public class StringValidatorHandler implements ValidatorHandler
{
    private final String encoding;
    private int pendingCharBytes;

    public StringValidatorHandler(StringValidatorConfig config)
    {
        this.encoding = config.encoding;
    }

    @Override
    public boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid;
        if ((flags & FLAGS_INIT) != 0x00)
        {
            pendingCharBytes = 0;
        }

        final int limit = index + length;
        validate:
        while (index < limit)
        {
            final int charByte0 = data.getByte(index);
            final int charByteCount = (charByte0 & 0b1000_0000) != 0
                    ? Integer.numberOfLeadingZeros((~charByte0 & 0xff) << 24)
                    : 1;

            if (pendingCharBytes > 0)
            {
                if ((charByte0 & 0b11000000) != 0b10000000)
                {
                    break;
                }
                pendingCharBytes--;
                index++;
            }
            else
            {
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
        }

        pendingCharBytes = limit - index;

        if ((flags & FLAGS_FIN) != 0x00)
        {
            valid = pendingCharBytes == 0 && index == limit;
        }
        else
        {
            valid = index + pendingCharBytes == limit;
        }
        return valid;
    }
}