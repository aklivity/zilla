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

import static io.aklivity.zilla.runtime.validator.core.StringValidator.ENCODING_VALIDATORS;
import static io.aklivity.zilla.runtime.validator.core.StringValidator.INVALID_ENCODING;

import java.util.function.Predicate;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;
import io.aklivity.zilla.runtime.validator.core.config.StringValidatorConfig;

public class StringFragmentValidator implements FragmentValidator
{
    private Predicate<byte[]> predicate;

    public StringFragmentValidator(
        StringValidatorConfig config)
    {
        this.predicate = ENCODING_VALIDATORS.getOrDefault(config.encoding, INVALID_ENCODING);
    }

    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        int valLength = 0;
        if ((flags & FLAGS_FIN) != 0x00)
        {
            valLength = -1;
            byte[] payloadBytes = new byte[length];
            data.getBytes(0, payloadBytes);

            if (predicate.test(payloadBytes))
            {
                next.accept(flags, data, index, length);
                valLength = length;
            }
        }
        return valLength;
    }
}
