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
package io.aklivity.zilla.runtime.model.core.internal;

import static io.aklivity.zilla.runtime.engine.model.ValidatorHandler.FLAGS_FIN;
import static io.aklivity.zilla.runtime.engine.model.ValidatorHandler.FLAGS_INIT;

import org.agrona.DirectBuffer;

public enum StringValidatorEncoding
{
    UTF_8
    {
        private int pendingCharBytes;

        @Override
        public boolean validate(
            int flags,
            DirectBuffer data,
            int index,
            int length)
        {
            if ((flags & FLAGS_INIT) != 0x00)
            {
                pendingCharBytes = 0;
            }

            final int limit = index + length;

            while (index < limit)
            {
                final int charByte0 = data.getByte(index);

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
                    final int charByteCount = (charByte0 & 0b1000_0000) != 0
                        ? Integer.numberOfLeadingZeros((~charByte0 & 0xff) << 24)
                        : 1;
                    final int charByteLimit = index + charByteCount;
                    for (int charByteIndex = index + 1; charByteIndex < charByteLimit; charByteIndex++)
                    {
                        if (charByteIndex >= limit || (data.getByte(charByteIndex) & 0b11000000) != 0b10000000)
                        {
                            pendingCharBytes = charByteLimit - charByteIndex;
                            break;
                        }
                    }
                    index += pendingCharBytes == 0 ? charByteCount : pendingCharBytes;
                }
            }

            return (flags & FLAGS_FIN) == 0x00
                ? index == limit
                : pendingCharBytes == 0 && index == limit;
        }
    };

    public abstract boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length);

    public static StringValidatorEncoding of(
        String encoding)
    {
        switch (encoding)
        {
        default:
            return UTF_8;
        }
    }
}
