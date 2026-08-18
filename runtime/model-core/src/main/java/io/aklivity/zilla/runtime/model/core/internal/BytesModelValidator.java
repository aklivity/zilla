/*
 * Copyright 2021-2026 Aklivity Inc
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

import java.util.function.Supplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

// bytes is opaque: any byte sequence, of any length, is a valid value. There is no field traversal, no
// schema, and no catalog to validate against, so every fragment is unconditionally VALID.
final class BytesModelValidator implements CoreModelValidator
{
    static Supplier<CoreModelValidator> supplier()
    {
        return BytesModelValidator::new;
    }

    private BytesModelValidator()
    {
    }

    @Override
    public Validity validate(
        int flags,
        DirectBufferEx data,
        int index,
        int length)
    {
        return Validity.VALID;
    }
}
