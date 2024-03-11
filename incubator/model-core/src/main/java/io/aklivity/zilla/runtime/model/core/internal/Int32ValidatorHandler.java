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

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ValidatorHandler implements ValidatorHandler
{
    private final int max;
    private final int min;
    private final int multiple;
    private final boolean exclusiveMax;
    private final boolean exclusiveMin;

    private boolean valid;
    private IntegerFormat format;
    private boolean number;
    private boolean sign;
    int decimalPlaces;
    private int value;
    private int pendingBytes;

    public Int32ValidatorHandler(
        Int32ModelConfig config)
    {
        this.max = config.max;
        this.min = config.min;
        this.exclusiveMax = config.exclusiveMax;
        this.exclusiveMin = config.exclusiveMin;
        this.multiple = config.multiple;
        this.format = IntegerFormat.of(config.format);
    }

    @Override
    public boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int progress = 0;
        if ((flags & FLAGS_INIT) != 0x00)
        {
            valid = true;
            value = 0;
            pendingBytes = 4;
            decimalPlaces = 0;
            sign = false;
            number = false;
            byte digit = data.getByte(index);
            if (format.negative(digit))
            {
                progress += BitUtil.SIZE_OF_BYTE;
                sign = true;
            }
        }

        if (valid)
        {
            pendingBytes = pendingBytes - length;
            for (int numByteIndex = index + progress; numByteIndex < index + length; numByteIndex++)
            {
                byte digit = data.getByte(numByteIndex);
                if (format.digit(digit))
                {
                    value = format.decode(value, digit);
                    number = true;
                }
                else
                {
                    break;
                }
                progress += BitUtil.SIZE_OF_BYTE;
            }

            if ((flags & FLAGS_FIN) != 0x00 && format.validateFin(pendingBytes, number))
            {
                if (sign)
                {
                    value *= -1;
                }
                valid = conditions(value, max, min, exclusiveMax, exclusiveMin, multiple);
            }
            else
            {
                valid = format.validateContinue(pendingBytes, length, progress);
            }
        }
        return valid;
    }

    private boolean conditions(
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
}
