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
import io.aklivity.zilla.runtime.model.core.config.Int64ModelConfig;

public class Int64ConverterHandler implements ConverterHandler
{
    private final Int64ValidatorHandler handler;

    public Int64ConverterHandler(
        Int64ModelConfig config,
        EngineContext context)
    {
        this.handler = new Int64ValidatorHandler(config, context);
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
        boolean valid = handler.validate(traceId, bindingId, FLAGS_COMPLETE, data, index, length, next);

        if (valid)
        {
            next.accept(data, index, length);
        }
        return valid ? length : VALIDATION_FAILURE;
    }
}
