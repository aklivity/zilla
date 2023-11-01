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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;
import io.aklivity.zilla.runtime.validator.core.config.LongValidatorConfig;

public class LongValueValidator implements ValueValidator
{
    public LongValueValidator(
        LongValidatorConfig config)
    {
    }

    @Override
    public int validate(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean valid = length == 8;

        if (valid)
        {
            next.accept(data, index, length);
        }

        return valid ? length : -1;
    }
}
