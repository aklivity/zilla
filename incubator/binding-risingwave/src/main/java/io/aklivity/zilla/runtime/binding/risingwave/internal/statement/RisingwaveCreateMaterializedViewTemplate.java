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

import java.util.LinkedHashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableInfo;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ViewInfo;


public class RisingwaveCreateMaterializedViewTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS %s;\u0000""";
    private final String fieldFormat = "%s, ";
    private final String includeFormat = "COALESCE(%s, %s_header::varchar) as %s, ";
    private final String timestampFormat = "COALESCE(%s, %s_timestamp::varchar) as %s, ";

    public RisingwaveCreateMaterializedViewTemplate()
    {
    }

    public String generate(
        ViewInfo viewInfo)
    {
        String name = viewInfo.name();
        String select = viewInfo.select();

        return String.format(sqlFormat, name, select);
    }

    public String generate(
        TableInfo tableInfo)
    {
        String name = tableInfo.name();

        String select = "*";

        Map<String, String> includes = tableInfo.columns().entrySet().stream()
            .filter(e -> ZILLA_MAPPINGS.containsKey(e.getKey()))
            .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

        if (!includes.isEmpty())
        {
            fieldBuilder.setLength(0);

            tableInfo.columns().keySet()
                .stream()
                .filter(c -> !ZILLA_MAPPINGS.containsKey(c))
                .forEach(c -> fieldBuilder.append(String.format(fieldFormat, c)));

            includes.forEach((k, v) ->
            {
                if (ZILLA_TIMESTAMP.equals(k))
                {
                    fieldBuilder.append(String.format(timestampFormat,  k, k, k));
                }
                else
                {
                    fieldBuilder.append(String.format(includeFormat, k, k, k));
                }
            });

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());
            select = fieldBuilder.toString();
        }

        return String.format(sqlFormat, "%s_view".formatted(name), "SELECT %s FROM %s_source".formatted(select, name));
    }
}
