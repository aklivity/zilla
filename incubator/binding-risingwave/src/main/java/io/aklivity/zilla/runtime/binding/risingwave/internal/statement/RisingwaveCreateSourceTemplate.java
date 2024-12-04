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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Stream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Table;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class RisingwaveCreateSourceTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE SOURCE IF NOT EXISTS %s (*)%s
        WITH (
           connector='kafka',
           properties.bootstrap.server='%s',
           topic='%s.%s',
           scan.startup.mode='latest',
           scan.startup.timestamp.millis='%d'
        ) FORMAT PLAIN ENCODE AVRO (
           schema.registry = '%s'
        );\u0000""";

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final long scanStartupMil;

    public RisingwaveCreateSourceTemplate(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil)
    {
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.scanStartupMil = scanStartupMil;
    }

    public String generateStreamSource(
        Stream stream)
    {
        String schema = stream.schema();
        String table = stream.name();

        includeBuilder.setLength(0);
        Map<String, String> includes = stream.columns().entrySet().stream()
            .filter(e -> ZILLA_MAPPINGS.containsKey(e.getKey()))
            .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

        if (!includes.isEmpty())
        {
            includeBuilder.append("\n");
            includes.forEach((k, v) -> includeBuilder.append(String.format(ZILLA_MAPPINGS.get(k), k)));
            includeBuilder.delete(includeBuilder.length() - 1, includeBuilder.length());
        }

        return String.format(sqlFormat, table, includeBuilder, bootstrapServer, schema, table, scanStartupMil, schemaRegistry);
    }

    public String generateTableSource(
        Table tableInfo)
    {
        String schema = tableInfo.schema();
        String table = tableInfo.name();
        String sourceName = "%s_source".formatted(table);

        includeBuilder.setLength(0);
        List<TableColumn> includes = tableInfo.columns().stream()
            .filter(column -> column.constraints().stream()
                .anyMatch(ZILLA_MAPPINGS::containsKey))
            .collect(Collectors.toCollection(ArrayList::new));

        if (!includes.isEmpty())
        {
            includeBuilder.append("\n");
            includes.forEach(i ->
            {
                String name = i.name();

                i.constraints().stream()
                    .filter(ZILLA_MAPPINGS::containsKey)
                    .findFirst()
                    .ifPresent(c ->
                    {
                        if (ZILLA_TIMESTAMP.equals(c))
                        {
                            includeBuilder.append(String.format(ZILLA_MAPPINGS.get(c), "%s_timestamp".formatted(name)));
                        }
                        else
                        {
                            includeBuilder.append(String.format(ZILLA_MAPPINGS.get(c), "%s_header".formatted(name)));
                        }
                    });

            });
            includeBuilder.delete(includeBuilder.length() - 1, includeBuilder.length());
        }

        return String.format(sqlFormat, sourceName, includeBuilder, bootstrapServer,
            schema, table, scanStartupMil, schemaRegistry);
    }
}
