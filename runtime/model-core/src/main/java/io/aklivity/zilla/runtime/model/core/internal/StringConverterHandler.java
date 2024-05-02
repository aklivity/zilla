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
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class StringConverterHandler implements ConverterHandler
{
    private final CoreModelEventContext event;
    private StringEncoding encoding;

    public StringConverterHandler(
        StringModelConfig config,
        EngineContext context)
    {
        this.encoding = StringEncoding.of(config.encoding);
        this.event = new CoreModelEventContext(context);
    }

    @Override
    public int convert(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;

        if (encoding.validate(data, index, length))
        {
            next.accept(data, index, length);
            valLength = length;
        }

        if (valLength == VALIDATION_FAILURE)
        {
            event.validationFailure(traceId, bindingId, StringModel.NAME);
        }

        return valLength;
    }
}
