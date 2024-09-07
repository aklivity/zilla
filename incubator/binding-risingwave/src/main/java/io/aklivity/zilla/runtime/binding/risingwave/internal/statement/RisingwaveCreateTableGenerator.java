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

import net.sf.jsqlparser.statement.create.table.CreateTable;

public class RisingwaveCreateTableGenerator extends StatementGenerator
{
    private final String bootstrapServer;
    private final String schemaRegistry;
    private final long scanStartupMil;

    private String table;
    private String primaryKey;


    public RisingwaveCreateTableGenerator(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil)
    {
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.scanStartupMil = scanStartupMil;
    }

    public String generate(
        CreateTable statement)
    {
        this.table = statement.getTable().getName();
        this.primaryKey = getPrimaryKey(statement);

        return format();
    }

    private String format()
    {
        builder.setLength(0);

        // Begin the CREATE TABLE statement
        builder.append("CREATE TABLE IF NOT EXISTS ");
        builder.append(table);
        builder.append(" (\n    *,\n    PRIMARY KEY (");
        builder.append(primaryKey);
        builder.append(")\n)");

        // Add the INCLUDE KEY statement
        builder.append(" INCLUDE KEY AS key\n");

        // Add WITH clause for connector properties
        builder.append("WITH (\n");
        builder.append("connector='kafka',\n");
        builder.append("properties.bootstrap.server='");
        builder.append(bootstrapServer);
        builder.append("',\n");
        builder.append("topic='");
        builder.append(table);
        builder.append(",'\n");
        builder.append("scan.startup.mode='latest',\n");
        builder.append("scan.startup.timestamp.millis='");
        builder.append(scanStartupMil);
        builder.append("'");
        builder.append("\n");

        // Add FORMAT and ENCODE
        builder.append(") FORMAT UPSERT ENCODE AVRO (\n");

        // Add schema properties
        builder.append("schema.registry = '");
        builder.append(schemaRegistry);
        builder.append("'\n");
        builder.append(");");
        builder.append("\u0000");

        return builder.toString();
    }
}
