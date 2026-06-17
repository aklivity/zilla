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

import java.util.ArrayList;
import java.util.List;

public final class YamlObjectNode extends YamlNode
{
    public final List<YamlEntry> entries;

    public YamlObjectNode(
        int line,
        int column,
        long offset)
    {
        super(line, column, offset);
        this.entries = new ArrayList<>();
    }

    public void add(
        YamlEntry entry)
    {
        entries.add(entry);
    }

    public void addAll(
        YamlObjectNode object)
    {
        entries.addAll(object.entries);
    }
}
