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

import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.core.config.IntegerModelConfig;

public class IntegerConverterHandler implements ConverterHandler
{
    private final int max;
    private final int min;
    private final int multiple;
    private final boolean exclusiveMax;
    private final boolean exclusiveMin;

    private IntegerFormat format;

    public IntegerConverterHandler(
        IntegerModelConfig config)
    {
        this.max = config.max;
        this.min = config.min;
        this.exclusiveMax = config.exclusiveMax;
        this.exclusiveMin = config.exclusiveMin;
        this.multiple = config.multiple;
        this.format = IntegerFormat.of(config.format);
    }

    @Override
    public int convert(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return format.validate(FLAGS_COMPLETE, data, index, length,
            max, min, exclusiveMax, exclusiveMin, multiple) ? length : -1;
    }
}
