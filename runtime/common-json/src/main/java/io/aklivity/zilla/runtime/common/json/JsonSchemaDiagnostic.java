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
package io.aklivity.zilla.runtime.common.json;

import java.util.Objects;

public final class JsonSchemaDiagnostic
{
    private final long line;
    private final long column;
    private final String pointer;
    private final String keyword;
    private final String message;

    public JsonSchemaDiagnostic(
        long line,
        long column,
        String pointer,
        String keyword,
        String message)
    {
        this.line = line;
        this.column = column;
        this.pointer = pointer;
        this.keyword = keyword;
        this.message = message;
    }

    public long line()
    {
        return line;
    }

    public long column()
    {
        return column;
    }

    public String pointer()
    {
        return pointer;
    }

    public String keyword()
    {
        return keyword;
    }

    public String message()
    {
        return message;
    }

    @Override
    public String toString()
    {
        return "[" + line + "," + column + "][" + pointer + "] " + message;
    }

    @Override
    public boolean equals(
        Object other)
    {
        boolean equal = this == other;
        if (!equal && other instanceof JsonSchemaDiagnostic that)
        {
            equal = line == that.line &&
                column == that.column &&
                Objects.equals(pointer, that.pointer) &&
                Objects.equals(keyword, that.keyword) &&
                Objects.equals(message, that.message);
        }
        return equal;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(line, column, pointer, keyword, message);
    }
}
