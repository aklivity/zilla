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
package io.aklivity.zilla.runtime.common.avro.internal;

import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroType;

final class AvroFieldImpl implements AvroField
{
    private final String name;
    private final AvroNode type;

    AvroFieldImpl(
        String name,
        AvroNode type)
    {
        this.name = name;
        this.type = type;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public AvroType type()
    {
        return type;
    }
}
