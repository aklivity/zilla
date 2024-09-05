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
        builder.append("'\n");
        builder.append("scan.startup.mode='latest',\n");
        builder.append("scan.startup.timestamp.millis='");
        builder.append(scanStartupMil);
        builder.append("'");
        builder.append("\n) ");
        builder.append("\n");

        // Add FORMAT and ENCODE
        builder.append(") FORMAT UPSERT ENCODE AVRO (\n");

        // Add schema properties
        builder.append("schema.registry = '");
        builder.append(schemaRegistry);
        builder.append("'\n");
        builder.append(");");

        return builder.toString();
    }
}
