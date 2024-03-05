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

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;

public enum IntegerFormat
{
    TEXT
    {
        private boolean number;
        private boolean sign;
        int decimalPlaces;
        private int value;

        @Override
        public boolean validate(
            int flags,
            DirectBuffer data,
            int index,
            int length,
            int max,
            int min,
            boolean exclusiveMax,
            boolean exclusiveMin,
            int multiple)
        {
            boolean valid = false;

            int progress = 0;
            if ((flags & FLAGS_INIT) != 0x00)
            {
                value = 0;
                decimalPlaces = 0;
                sign = false;
                number = false;
                byte digit = data.getByte(index);
                if (digit == '-')
                {
                    progress += BitUtil.SIZE_OF_BYTE;
                    sign = true;
                }
            }

            for (int numByteIndex = index + progress; numByteIndex < index + length; numByteIndex++)
            {
                byte digit = data.getByte(numByteIndex);

                if (digit >= '0' && digit <= '9')
                {
                    value = value * 10 + (digit - '0');
                    number = true;
                }
                else
                {
                    break;
                }
                progress += BitUtil.SIZE_OF_BYTE;
            }

            if ((flags & FLAGS_FIN) != 0x00 && number)
            {
                if (sign)
                {
                    value *= -1;
                }
                if (conditions(value, max, min, exclusiveMax, exclusiveMin, multiple))
                {
                    valid = true;
                }
            }
            else
            {
                valid = length == progress;
            }

            return valid;
        }
    },

    BINARY
    {
        private int pendingBytes;
        private int value;

        @Override
        public boolean validate(
            int flags,
            DirectBuffer data,
            int index,
            int length,
            int max,
            int min,
            boolean exclusiveMax,
            boolean exclusiveMin,
            int multiple)
        {
            boolean valid;

            if ((flags & FLAGS_INIT) != 0x00)
            {
                value = 0;
                pendingBytes = 4;
            }

            pendingBytes = pendingBytes - length;

            for (int numByteIndex = index; numByteIndex < index + length; numByteIndex++)
            {
                value <<= 8;
                value |= data.getByte(numByteIndex) & 0xFF;
            }

            if ((flags & FLAGS_FIN) != 0x00 && pendingBytes == 0)
            {
                valid = conditions(value, max, min, exclusiveMax, exclusiveMin, multiple);
            }
            else
            {
                valid = pendingBytes >= 0;
            }
            return valid;
        }
    };

    private static boolean conditions(
        int value,
        int max,
        int min,
        boolean exclusiveMax,
        boolean exclusiveMin,
        int multiple)
    {
        boolean valid = false;

        if ((exclusiveMax ? value < max : value <= max) &&
            (exclusiveMin ? value > min : value >= min) &&
            value % multiple == 0)
        {
            valid = true;
        }

        return valid;
    }

    public abstract boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        int max,
        int min,
        boolean exclusiveMax,
        boolean exclusiveMin,
        int multiple);

    public static IntegerFormat of(
        String format)
    {
        switch (format)
        {
        case "text":
            return TEXT;
        default:
            return BINARY;
        }
    }
}
