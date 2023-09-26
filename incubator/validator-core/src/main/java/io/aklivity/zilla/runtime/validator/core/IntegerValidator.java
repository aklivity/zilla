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
import io.aklivity.zilla.runtime.validator.core.config.IntegerValidatorConfig;

public class IntegerValidator implements Validator
{
    public IntegerValidator(IntegerValidatorConfig config)
    {
    }

    @Override
    public boolean read(
        DirectBuffer data,
        int index,
        int length)
    {
        return validate(data, index, length);
    }

    @Override
    public boolean write(
        DirectBuffer data,
        int index,
        int length)
    {
        return validate(data, index, length);
    }

    private boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        return length == 4;
    }
}
