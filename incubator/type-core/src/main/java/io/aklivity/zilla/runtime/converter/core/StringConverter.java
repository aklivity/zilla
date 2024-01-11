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
package io.aklivity.zilla.runtime.converter.core;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.converter.core.config.StringConverterConfig;
import io.aklivity.zilla.runtime.engine.converter.Converter;
import io.aklivity.zilla.runtime.engine.converter.function.ValueConsumer;

public class StringConverter implements Converter
{
    private StringEncoding encoding;

    public StringConverter(
        StringConverterConfig config)
    {
        this.encoding = StringEncoding.of(config.encoding);
    }

    @Override
    public int convert(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validateComplete(data, index, length, next);
    }

    private int validateComplete(
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

        return valLength;
    }
}
