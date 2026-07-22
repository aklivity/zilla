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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.function.DoublePredicate;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;

final class FloatModelValidator implements CoreModelValidator
{
    private final FloatFormat format;
    private final DoublePredicate check;
    private final FloatState state;

    static Supplier<CoreModelValidator> supplier(
        FloatModelConfig config)
    {
        float max = config.max;
        float min = config.min;
        DoublePredicate checkMax = config.exclusiveMax ? v -> v < max : v -> v <= max;
        DoublePredicate checkMin = config.exclusiveMin ? v -> v > min : v -> v >= min;
        DoublePredicate checkMultiple = config.multiple != null ? v -> v % config.multiple == 0 : v -> true;
        DoublePredicate check = checkMax.and(checkMin).and(checkMultiple);
        FloatFormat format = FloatFormat.of(config.format);
        return () -> new FloatModelValidator(format, check);
    }

    private FloatModelValidator(
        FloatFormat format,
        DoublePredicate check)
    {
        this.format = format;
        this.check = check;
        this.state = new FloatState();
    }

    @Override
    public Validity validate(
        int flags,
        DirectBufferEx data,
        int index,
        int length)
    {
        if ((flags & FLAGS_INIT) != 0x00)
        {
            state.decoded = 0;
            state.processed = 0;
            state.value = 0;
            state.divider = 0;
        }
        int progress = format.decode(state, flags, data, index, length);
        Validity validity = progress != FloatFormat.INVALID_INDEX ? Validity.VALID : Validity.MALFORMED;
        if ((flags & FLAGS_FIN) != 0x00 && validity == Validity.VALID)
        {
            validity = !format.valid(state) ? Validity.MALFORMED
                : !check.test(state.value) ? Validity.INVALID
                : Validity.VALID;
        }
        return validity;
    }
}
