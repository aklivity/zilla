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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringValidatorHandler implements ValidatorHandler
{
    private final StringValidatorEncoding encoding;
    private final CoreModelEventContext event;

    public StringValidatorHandler(
        StringModelConfig config,
        EngineContext context)
    {
        this.encoding = StringValidatorEncoding.of(config.encoding);
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
        boolean valid = encoding.validate(flags, data, index, length);

        if (!valid)
        {
            event.validationFailure(traceId, bindingId, StringModel.NAME);
        }

        return valid;
    }
}
