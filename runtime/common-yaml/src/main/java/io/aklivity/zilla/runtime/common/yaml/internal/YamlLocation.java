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

public final class YamlLocation
{
    private final int line;
    private final int column;
    private final long offset;

    public YamlLocation(
        int line,
        int column,
        long offset)
    {
        this.line = line;
        this.column = column;
        this.offset = offset;
    }

    public long line()
    {
        return line;
    }

    public long column()
    {
        return column;
    }

    public long offset()
    {
        return offset;
    }
}
