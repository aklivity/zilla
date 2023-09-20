/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.validator;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.internal.validator.config.LongValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public class LongValidator implements Validator
{
    public LongValidator(
        LongValidatorConfig config)
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
        return length == 8;
    }
}
