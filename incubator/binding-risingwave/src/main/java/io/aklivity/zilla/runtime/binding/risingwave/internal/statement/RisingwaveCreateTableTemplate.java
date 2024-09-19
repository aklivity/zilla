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

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;

public class RisingwaveCreateTableTemplate extends RisingwaveCommandTemplate
{
    private final String sqlFormat = """
        CREATE TABLE IF NOT EXISTS %s (
            *,
            PRIMARY KEY (%s)
        ) INCLUDE KEY AS key
        WITH (
           connector='kafka',
           properties.bootstrap.server='%s',
           topic='%s.%s',
           scan.startup.mode='latest',
           scan.startup.timestamp.millis='%d'
        ) FORMAT UPSERT ENCODE AVRO (
           schema.registry = '%s'
        );\u0000""";

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final long scanStartupMil;

    public RisingwaveCreateTableTemplate(
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
        Statement statement)
    {
        CreateTable createTable = (CreateTable) statement;
        String table = createTable.getTable().getName();
        String primaryKey = getPrimaryKey(createTable);

        return String.format(sqlFormat, table, primaryKey, bootstrapServer, database,
            table, scanStartupMil, schemaRegistry);
    }
}
