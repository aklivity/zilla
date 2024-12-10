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
package io.aklivity.zilla.runtime.binding.risingwave.internal.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class RisingwaveCreateMaterializedViewTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS %s;\u0000""";
    private final String fieldFormat = "%s, ";
    private final String includeFormat = "COALESCE(%s, %s_header::varchar) as %s, ";
    private final String timestampFormat = "COALESCE(%s, %s_timestamp::varchar) as %s, ";

    public String generate(
        CreateZview createZview)
    {
        String name = createZview.name();
        String select = createZview.select();

        return String.format(sqlFormat, name, select);
    }

    public String generate(
        CreateTable createTable)
    {
        String name = createTable.name();

        String select = "*";

        List<TableColumn> includes = createTable.columns().stream()
            .filter(c -> ZILLA_MAPPINGS.containsKey(c.name()))
            .collect(Collectors.toCollection(ArrayList::new));

        if (!includes.isEmpty())
        {
            fieldBuilder.setLength(0);

            createTable.columns()
                .stream()
                .filter(c -> !ZILLA_MAPPINGS.containsKey(c.name()))
                .forEach(c -> fieldBuilder.append(String.format(fieldFormat, c.name())));

            includes.forEach(c ->
            {
                String columnName = c.name();
                if (ZILLA_TIMESTAMP.equals(columnName))
                {
                    fieldBuilder.append(String.format(timestampFormat,  columnName, columnName, columnName));
                }
                else
                {
                    fieldBuilder.append(String.format(includeFormat, columnName, columnName, columnName));
                }
            });

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());
            select = fieldBuilder.toString();
        }

        return String.format(sqlFormat, "%s_view".formatted(name), "SELECT %s FROM %s_source".formatted(select, name));
    }
}
