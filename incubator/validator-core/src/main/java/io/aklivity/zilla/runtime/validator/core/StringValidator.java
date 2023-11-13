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
package io.aklivity.zilla.runtime.validator.core;

import static io.aklivity.zilla.runtime.validator.core.StringEncoding.ENCODING_VALIDATORS;
import static io.aklivity.zilla.runtime.validator.core.StringEncoding.INVALID_ENCODING;

import java.util.function.Predicate;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringValidator implements ValueValidator, FragmentValidator
{
    private Predicate<byte[]> predicate;

    public StringValidator(
        StringValidatorConfig config)
    {
        this.predicate = ENCODING_VALIDATORS.getOrDefault(config.encoding, INVALID_ENCODING);
    }

    @Override
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return validateComplete(data, index, length, next);
    }

    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        return flags == FLAGS_COMPLETE
            ? validateComplete(data, index, length, (b, i, l) -> next.accept(FLAGS_COMPLETE, b, i, l))
            : 0;
    }

    private int validateComplete(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        int valLength = -1;
        byte[] payloadBytes = new byte[length];
        data.getBytes(index, payloadBytes);

        if (predicate.test(payloadBytes))
        {
            next.accept(data, index, length);
            valLength = length;
        }

        return valLength;
    }
}
