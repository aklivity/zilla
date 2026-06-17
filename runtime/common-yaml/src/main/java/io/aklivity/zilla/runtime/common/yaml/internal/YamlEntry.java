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
package io.aklivity.zilla.runtime.common.yaml.internal;

public final class YamlEntry
{
    public final String name;
    public final YamlNode key;
    public final YamlNode value;
    public final int line;
    public final int column;
    public final long offset;

    public YamlEntry(
        String name,
        YamlNode value,
        int line,
        int column,
        long offset)
    {
        this(name, null, value, line, column, offset);
    }

    public YamlEntry(
        YamlNode key,
        YamlNode value,
        int line,
        int column,
        long offset)
    {
        this(null, key, value, line, column, offset);
    }

    private YamlEntry(
        String name,
        YamlNode key,
        YamlNode value,
        int line,
        int column,
        long offset)
    {
        this.name = name;
        this.key = key;
        this.value = value;
        this.line = line;
        this.column = column;
        this.offset = offset;
    }
}
