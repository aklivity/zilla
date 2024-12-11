package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;

public class RisingwaveCreateZtableMacro extends RisingwaveBaseMacro
{
    private final String createTopicSqlFormat = """
        CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";
    private final String primaryKeyFormat = ", PRIMARY KEY (%s)";

    private final String createSourceSqlFormat = """
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

    private final StringBuilder fieldBuilder;
    private final StringBuilder includeBuilder;

    private final RisingwaveTemplate createTopic = this::generateCreateTopic;
    private final RisingwaveTemplate createSource = this::generateCreateSource;

    private RisingwaveTemplate template;

    public RisingwaveCreateZtableMacro(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil)
    {
        this.fieldBuilder = new StringBuilder();
        this.includeBuilder = new StringBuilder();
        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.scanStartupMil = scanStartupMil;
        this.template = createTopic;
    }

    private String generate(
        CreateTable createTable)
    {
        return template.generate(createTable);
    }

    private String generateCreateTopic(
        Object model)
    {
       CreateTable createTable = (CreateTable) model;
        String topic = createTable.name();
        String primaryKey = !createTable.primaryKeys().isEmpty()
            ? String.format(primaryKeyFormat, createTable.primaryKeys().stream().findFirst().get())
            : "";

        fieldBuilder.setLength(0);

        createTable.columns().forEach(c ->
        {
            fieldBuilder.append(c.name());
            fieldBuilder.append(" ");
            fieldBuilder.append(c.type());
            if (!c.constraints().isEmpty())
            {
                fieldBuilder.append(" ");
                c.constraints().forEach(fieldBuilder::append);
            }
            fieldBuilder.append(", ");
        });

        fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

        this.template = createSource;

        return String.format(createTopicSqlFormat, topic, fieldBuilder, primaryKey);
    }

    private String generateCreateSource(
        Object model)
    {
        CreateTable createTable = (CreateTable) model;
        String schema = createTable.schema();
        String table = createTable.name();
        String sourceName = "%s_source".formatted(table);

        includeBuilder.setLength(0);
        List<TableColumn> includes = createTable.columns().stream()
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

        return String.format(createSourceSqlFormat, sourceName, includeBuilder, bootstrapServer,
                    schema, table, scanStartupMil, schemaRegistry);

    }
}
