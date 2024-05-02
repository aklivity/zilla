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

import java.util.function.IntPredicate;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.Int32ModelConfig;

public class Int32ValidatorHandler implements ValidatorHandler
{
    private final Int32Format format;
    private final IntPredicate check;
    private final Int32State state;
    private final CoreModelEventContext event;

    public Int32ValidatorHandler(
        Int32ModelConfig config,
        EngineContext context)
    {
        int max = config.max;
        int min = config.min;
        IntPredicate checkMax = config.exclusiveMax ? v -> v < max : v -> v <= max;
        IntPredicate checkMin = config.exclusiveMin ? v -> v > min : v -> v >= min;
        IntPredicate checkMultiple = v -> v % config.multiple == 0;
        this.check = checkMax.and(checkMin).and(checkMultiple);
        this.format = Int32Format.of(config.format);
        this.state = new Int32State();
        this.event = new CoreModelEventContext(context);
    }

    @Override
    public boolean validate(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        if ((flags & FLAGS_INIT) != 0x00)
        {
            state.decoded = 0;
            state.processed = 0;
        }
        int progress = format.decode(state, data, index, length);
        boolean valid = progress != Int32Format.INVALID_INDEX;
        if ((flags & FLAGS_FIN) != 0x00 && valid)
        {
            valid &= format.valid(state);
            valid &= check.test(state.decoded);
        }

        if (!valid)
        {
            event.validationFailure(traceId, bindingId, Int32Model.NAME);
        }
        return valid;
    }
}
