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

import io.aklivity.zilla.runtime.engine.validator.Validator;
import io.aklivity.zilla.runtime.engine.validator.function.ToIntValueFunction;
import io.aklivity.zilla.runtime.validator.core.config.LongValidatorConfig;

public class LongValidator implements Validator
{
    public LongValidator(
        LongValidatorConfig config)
    {
    }

    @Override
    public int read(
        DirectBuffer data,
        int index,
        int length,
        ToIntValueFunction next)
    {
        return !validate(data, index, length) ? -1 : next.applyAsInt(data, index, length);
    }

    @Override
    public int write(
        DirectBuffer data,
        int index,
        int length,
        ToIntValueFunction next)
    {
        return !validate(data, index, length) ? -1 : next.applyAsInt(data, index, length);
    }

    private boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        return length == 8;
    }
}
