/*
 * Copyright 2021-2023 Aklivity Inc
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

import java.util.Map;

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

    public String generate(
        String database,
        RisingwaveCreateTableCommand command)
    {
        return generate(database, "", command);
    }

    public String generate(
        String database,
        String prefix,
        RisingwaveCreateTableCommand command)
    {
        String table = command.createTable.getTable().getName();
        String sourceName = "%s%s".formatted(table, prefix);

        includeBuilder.setLength(0);
        final Map<String, String> includes = command.includes;
        if (includes != null && !includes.isEmpty())
        {
            includeBuilder.append("\n");
            includes.forEach((k, v) -> includeBuilder.append(String.format(ZILLA_MAPPINGS.get(k), v)));
            includeBuilder.delete(includeBuilder.length() - 1, includeBuilder.length());
        }

        return String.format(sqlFormat, sourceName, includeBuilder, bootstrapServer, database, table, scanStartupMil, schemaRegistry);
    }
}
