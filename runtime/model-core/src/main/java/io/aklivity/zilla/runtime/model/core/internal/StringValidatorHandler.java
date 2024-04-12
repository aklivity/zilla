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
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringValidatorHandler implements ValidatorHandler
{
    private final StringValidatorEncoding encoding;
    private final IntPredicate check;
    private final StringState state;
    private final Pattern pattern;
    private final ExpandableDirectByteBuffer buffer;
    private final CoreModelEventContext event;

    public StringValidatorHandler(
        StringModelConfig config,
        EngineContext context)
    {
        this.encoding = StringValidatorEncoding.of(config.encoding);
        this.state = new StringState();
        int maxLength = config.maxLength;
        IntPredicate max = maxLength > 0 ? v -> v <= maxLength : v -> true;
        int minLength = config.minLength;
        IntPredicate min = minLength > 0 ? v -> v >= minLength : v -> true;
        this.check = max.and(min);
        String exp = config.pattern;
        this.pattern = exp != null ? Pattern.compile(exp) : null;
        this.buffer = new ExpandableDirectByteBuffer();
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
            state.processed = 0;
            state.length = 0;
        }

        if (pattern != null)
        {
            buffer.putBytes(state.length, data, index, length);
        }

        boolean valid = encoding.validate(state, flags, data, index, length);

        if (pattern != null && valid && (flags & FLAGS_FIN) != 0x00)
        {
            valid = pattern.matcher(buffer.getStringWithoutLengthUtf8(0, state.length)).matches();
        }

        valid = (flags & FLAGS_FIN) == 0x00
            ? valid
            : state.processed == 0 && valid && check.test(state.length);

        if (!valid)
        {
            event.validationFailure(traceId, bindingId, StringModel.NAME);
        }

        return valid;
    }
}
