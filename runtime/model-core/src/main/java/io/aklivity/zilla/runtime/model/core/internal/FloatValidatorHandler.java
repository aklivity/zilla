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

import java.util.function.DoublePredicate;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.FloatModelConfig;

public class FloatValidatorHandler implements ValidatorHandler
{
    private final FloatFormat format;
    private final DoublePredicate check;
    private final FloatState state;
    private final CoreModelEventContext event;

    public FloatValidatorHandler(
        FloatModelConfig config,
        EngineContext context)
    {
        float max = config.max;
        float min = config.min;
        DoublePredicate checkMax = config.exclusiveMax ? v -> v < max : v -> v <= max;
        DoublePredicate checkMin = config.exclusiveMin ? v -> v > min : v -> v >= min;
        DoublePredicate checkMultiple = config.multiple != null ? v -> v % config.multiple == 0 : v -> true;
        this.check = checkMax.and(checkMin).and(checkMultiple);
        this.format = FloatFormat.of(config.format);
        this.state = new FloatState();
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
            state.value = 0;
            state.divider = 0;
        }
        int progress = format.decode(state, flags, data, index, length);
        boolean valid = progress != FloatFormat.INVALID_INDEX;
        if ((flags & FLAGS_FIN) != 0x00 && valid)
        {
            valid &= format.valid(state);
            valid &= check.test(state.value);
        }

        if (!valid)
        {
            event.validationFailure(traceId, bindingId, FloatModel.NAME);
        }

        return valid;
    }
}
