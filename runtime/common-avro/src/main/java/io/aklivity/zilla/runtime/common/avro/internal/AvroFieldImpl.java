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
package io.aklivity.zilla.runtime.common.avro.internal;

import java.util.List;

import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.common.avro.AvroField;
import io.aklivity.zilla.runtime.common.avro.AvroType;

final class AvroFieldImpl implements AvroField
{
    private final String name;
    private final AvroNode type;
    private final String[] aliases;
    private final JsonValue defaultValue;

    AvroFieldImpl(
        String name,
        AvroNode type,
        String[] aliases,
        JsonValue defaultValue)
    {
        this.name = name;
        this.type = type;
        this.aliases = aliases;
        this.defaultValue = defaultValue;
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

    @Override
    public List<String> aliases()
    {
        return aliases == null ? List.of() : List.of(aliases);
    }

    @Override
    public JsonValue defaultValue()
    {
        return defaultValue;
    }
}
