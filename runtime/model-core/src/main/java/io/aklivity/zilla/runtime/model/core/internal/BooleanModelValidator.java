/*
 * Copyright 2021-2024 Aklivity Inc
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

final class BooleanModelValidator implements CoreModelValidator
{
    private static final byte TRUE = 0x01;
    private static final byte FALSE = 0x00;

    @Override
    public boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length)
    {
        boolean valid = false;

        if (length == 1 && (flags & FLAGS_COMPLETE) != 0x00)
        {
            byte value = data.getByte(index);
            valid = value == TRUE || value == FALSE;
        }

        return valid;
    }
}
