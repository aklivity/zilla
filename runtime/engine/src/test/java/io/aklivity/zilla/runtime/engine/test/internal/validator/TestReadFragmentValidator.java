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
package io.aklivity.zilla.runtime.engine.test.internal.validator;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.function.FragmentConsumer;

public class TestReadFragmentValidator implements FragmentValidator
{
    @Override
    public int validate(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        return (flags & FLAGS_FIN) != 0x00 ? validateFin(flags, data, index, length, next) : 0;
    }

    private int validateFin(
        int flags,
        DirectBuffer data,
        int index,
        int length,
        FragmentConsumer next)
    {
        boolean valid = length == 18;
        if (valid)
        {
            next.accept(flags, data, index, length);
        }
        return valid ? length : -1;
    }
}
