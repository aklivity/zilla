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

final class YamlEntry
{
    final String name;
    final YamlNode value;
    final int line;
    final int column;
    final long offset;
    final boolean merged;

    YamlEntry(
        String name,
        YamlNode value,
        int line,
        int column,
        long offset)
    {
        this(name, value, line, column, offset, false);
    }

    YamlEntry(
        String name,
        YamlNode value,
        int line,
        int column,
        long offset,
        boolean merged)
    {
        this.name = name;
        this.value = value;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.merged = merged;
    }
}
