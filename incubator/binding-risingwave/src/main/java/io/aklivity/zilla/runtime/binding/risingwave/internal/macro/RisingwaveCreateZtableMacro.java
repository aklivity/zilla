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
package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateTable;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.TableColumn;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZtableMacro extends RisingwaveMacroBase
{
    private static final String ZTABLE_NAME = "ztables";
    private static final String TABLE_NAME = "tables";

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final long scanStartupMil;

    private final StringBuilder fieldBuilder;
    private final StringBuilder includeBuilder;
    private final String systemSchema;
    private final String user;
    private final CreateTable command;

    public RisingwaveCreateZtableMacro(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil,
        String systemSchema,
        String user,
        String sql,
        CreateTable command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

        this.systemSchema = systemSchema;
        this.user = user;
        this.command = command;
        this.fieldBuilder = new StringBuilder();
        this.includeBuilder = new StringBuilder();

        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.scanStartupMil = scanStartupMil;
    }

    public RisingwaveMacroState start()
    {
        return new CreateTopicState();
    }

    private final class CreateTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";
        private final String primaryKeyFormat = ", PRIMARY KEY (%s)";

        private final StringBuilder fieldBuilder = new StringBuilder();

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();
            String primaryKey = !command.primaryKeys().isEmpty()
                ? String.format(primaryKeyFormat, command.primaryKeys().stream().findFirst().get())
                : "";

            fieldBuilder.setLength(0);

            command.columns().forEach(c ->
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

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder, primaryKey);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSourceState state = new CreateSourceState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class CreateSourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE SOURCE IF NOT EXISTS %s_source (*)%s
            WITH (
               connector='kafka',
               properties.bootstrap.server='%s',
               topic='%s.%s',
               scan.startup.mode='latest',
               scan.startup.timestamp.millis='%d'
            ) FORMAT PLAIN ENCODE AVRO (
               schema.registry = '%s'
            );\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String schema = command.schema();
            String table = command.name();

            includeBuilder.setLength(0);
            List<TableColumn> includes = command.columns().stream()
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

            String sqlQuery = String.format(sqlFormat, table, includeBuilder, bootstrapServer,
                schema, table, scanStartupMil, schemaRegistry);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateMaterializedViewState state = new CreateMaterializedViewState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class CreateMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS %s;\u0000""";

        private final String includeFormat = "COALESCE(%s, %s_header::%s) as %s, ";
        private final String timestampFormat = "COALESCE(%s, %s_timestamp::%s) as %s, ";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String select = "*";
            List<TableColumn> includes = command.columns().stream()
                .filter(column -> column.constraints().stream()
                    .anyMatch(ZILLA_MAPPINGS::containsKey))
                .collect(Collectors.toCollection(ArrayList::new));

            if (!includes.isEmpty())
            {
                fieldBuilder.setLength(0);

                command.columns()
                    .forEach(i ->
                    {
                        String columnName = i.name();
                        String columnType = i.type().toLowerCase();

                        Optional<String> include = i.constraints().stream()
                            .filter(ZILLA_MAPPINGS::containsKey)
                            .findFirst();

                        if (include.isPresent())
                        {
                            final String includeName = include.get();
                            if (ZILLA_TIMESTAMP.equals(includeName))
                            {
                                fieldBuilder.append(
                                    String.format(timestampFormat, columnName, columnName, columnType, columnName));
                            }
                            else
                            {
                                fieldBuilder.append(
                                    String.format(includeFormat, columnName, columnName, columnType, columnName));
                            }
                        }
                        else
                        {
                            fieldBuilder.append("%s, ".formatted(columnName));
                        }
                    });

                fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());
                select = fieldBuilder.toString();
            }

            String sqlQuery = String.format(sqlFormat, "%s_view".formatted(name),
                "SELECT %s FROM %s_source".formatted(select, name));

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateTableState state = new CreateTableState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class CreateTableState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TABLE IF NOT EXISTS %s (%s%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();
            String primaryKeyFormat = ", PRIMARY KEY (%s)";
            String primaryKey = !command.primaryKeys().isEmpty()
                ? String.format(primaryKeyFormat, command.primaryKeys().stream().findFirst().get())
                : "";

            fieldBuilder.setLength(0);

            command.columns()
                .forEach(c -> fieldBuilder.append(
                    String.format("%s %s, ", c.name(), c.type())));

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder, primaryKey);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            GrantResourceState state = new GrantResourceState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class GrantResourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            GRANT ALL PRIVILEGES ON %s %s.%s TO %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, "TABLE", command.schema(), command.name(), user);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSinkIntoState state = new CreateSinkIntoState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class CreateSinkIntoState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE SINK %s.%s_view_sink INTO %s FROM %s_view;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();
            String sqlQuery = String.format(sqlFormat, systemSchema, name, name, name);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSinkState state = new CreateSinkState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }
    }

    private final class CreateSinkState implements RisingwaveMacroState
    {
        private final String sqlKafkaFormat = """
            CREATE SINK %s.%s_sink
            FROM %s
            WITH (
               connector='kafka',
               properties.bootstrap.server='%s',
               topic='%s.%s'%s
            ) FORMAT UPSERT ENCODE AVRO (
               schema.registry='%s'
            ) KEY ENCODE TEXT;\u0000""";


        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topicSchema = command.schema();
            String table = command.name();

            String sqlQuery = String.format(sqlKafkaFormat,
                systemSchema,
                table,
                table,
                bootstrapServer,
                topicSchema,
                table,
                ",\n   primary_key='%s'".formatted(command.primaryKeys().stream().findFirst().get()), schemaRegistry);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            InsertIntoCatalogState state = new InsertIntoCatalogState();
            state.onStarted(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doFlushProxy(traceId, authorization, flushEx);
            return errorState();
        }

        private final class InsertIntoCatalogState implements RisingwaveMacroState
        {
            private final String sqlFormat = """
                INSERT INTO %s.%s (name, sql) VALUES ('%s', '%s');\u0000""";

            @Override
            public void onStarted(
                long traceId,
                long authorization)
            {
                String name = command.name();

                String newSql = sql.replace(ZTABLE_NAME, TABLE_NAME)
                    .replace("\u0000", "");
                newSql = newSql.replaceAll("'", "''");
                String sqlQuery = String.format(sqlFormat, systemSchema, ZTABLE_NAME, name, newSql);

                handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
            }

            @Override
            public RisingwaveMacroState onCompletion(
                long traceId,
                long authorization,
                PgsqlFlushExFW flushEx)
            {
                handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_ZTABLE_COMMAND);
                return this;
            }

            @Override
            public RisingwaveMacroState onReady(
                long traceId,
                long authorization,
                PgsqlFlushExFW flushEx)
            {
                handler.doReady(traceId, authorization, sql.length());
                return null;
            }

            @Override
            public RisingwaveMacroState onError(
                long traceId,
                long authorization,
                PgsqlFlushExFW flushEx)
            {
                handler.doFlushProxy(traceId, authorization, flushEx);
                return this;
            }
        }
    }
}
