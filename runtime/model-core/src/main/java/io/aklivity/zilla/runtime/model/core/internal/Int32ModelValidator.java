/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.function.IntPredicate;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

final class Int32ModelValidator implements CoreModelValidator
{
    private final Int32Format format;
    private final IntPredicate check;
    private final Int32State state;

    static Supplier<CoreModelValidator> supplier(
        Int32ModelConfig config)
    {
        int max = config.max;
        int min = config.min;
        IntPredicate checkMax = config.exclusiveMax ? v -> v < max : v -> v <= max;
        IntPredicate checkMin = config.exclusiveMin ? v -> v > min : v -> v >= min;
        IntPredicate checkMultiple = v -> v % config.multiple == 0;
        IntPredicate check = checkMax.and(checkMin).and(checkMultiple);
        Int32Format format = Int32Format.of(config.format);
        return () -> new Int32ModelValidator(format, check);
    }

    private Int32ModelValidator(
        Int32Format format,
        IntPredicate check)
    {
        this.format = format;
        this.check = check;
        this.state = new Int32State();
    }

    @Override
    public Validity validate(
        int flags,
        DirectBuffer data,
        int index,
        int length)
    {
        if ((flags & FLAGS_INIT) != 0x00)
        {
            state.decoded = 0;
            state.processed = 0;
        }
        int progress = format.decode(state, data, index, length);
        Validity validity = progress != Int32Format.INVALID_INDEX ? Validity.VALID : Validity.MALFORMED;
        if ((flags & FLAGS_FIN) != 0x00 && validity == Validity.VALID)
        {
            // a fully-decoded value that fails the format's structural check is MALFORMED; one that decodes
            // cleanly but violates a range/multiple constraint is INVALID (relaxable under LENIENT)
            validity = !format.valid(state) ? Validity.MALFORMED
                : !check.test(state.decoded) ? Validity.INVALID
                : Validity.VALID;
        }
        return validity;
    }
}
