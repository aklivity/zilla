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

import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PgsqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Select;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZfunctionMacro extends RisingwaveMacroBase
{
    private static final String ZFUNCTION_NAME = "zfunctions";

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final PgsqlParser parser;

    private final long scanStartupMil;
    private final String systemSchema;
    private final String user;
    private final CreateZfunction command;

    public RisingwaveCreateZfunctionMacro(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil,
        String systemSchema,
        String user,
        String sql,
        CreateZfunction command,
        RisingwaveMacroHandler handler,
        PgsqlParser parser)
    {
        super(sql, handler);

        this.scanStartupMil = scanStartupMil;
        this.systemSchema = systemSchema;
        this.user = user;
        this.command = command;

        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
        this.parser = parser;
    }

    public RisingwaveMacroState start()
    {
        return new CreateSourceTopicState();
    }

    private final class CreateSourceTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC %s.%s_commands (%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            String arguments =  command.arguments().stream()
                .map(arg -> arg.name() + " " + arg.type())
                .collect(Collectors.joining(", "));

            String sqlQuery = String.format(sqlFormat, command.schema(), topic, arguments);

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
            CREATE SOURCE %s.%s_commands (*)
            INCLUDE header 'zilla:correlation-id' VARCHAR AS correlation_id
            INCLUDE header 'zilla:identity' VARCHAR AS owner_id
            INCLUDE timestamp AS created_at
            WITH (
               connector = 'kafka',
               properties.bootstrap.server = '%s',
               topic = '%s.%s_commands',
               scan.startup.mode = 'latest',
               scan.startup.timestamp.millis = '%d'
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

            String sqlQuery = String.format(sqlFormat, systemSchema, table, bootstrapServer,
                schema, table, scanStartupMil, schemaRegistry);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateEventMaterializedViewState state = new CreateEventMaterializedViewState();
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

    private final class CreateEventMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW %s.%s_events AS
                SELECT
                    c.correlation_id,
                    c.owner_id,
                    c.created_at,
                    %s
                FROM %s.%s_commands c
                %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            Select select = command.select();

            String columns =  select.columns().stream()
                .map(c -> c.replace("args", "c"))
                .collect(Collectors.joining(", "));

            String name = command.name();
            String from = command.name();

            String join = "";
            if (select.from() != null)
            {
                join = "JOIN %s".formatted(select.from());

                if (select.whereClause() != null)
                {
                    join = String.format("%s ON %s", join, select.whereClause()
                        .replace("args", "c"));
                }
            }

            String sqlQuery = String.format(sqlFormat, systemSchema, name, columns,
                systemSchema, from, join);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            RisingwaveMacroState state = new CreateSinkIntoState();
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
            CREATE SINK %s_sink_into INTO %s FROM %s_events;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            final String systemName = "%s.%s".formatted(systemSchema, command.name());
            final String events = command.events();

            String sqlQuery = String.format(sqlFormat, systemName, events, systemName);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSinkTopicState state = new CreateSinkTopicState();
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

    private final class CreateSinkTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC %s.%s_replies (status VARCHAR, correlation_id VARCHAR);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            String sqlQuery = String.format(sqlFormat, command.schema(), topic);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateReplySink state = new CreateReplySink();
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

    private final class CreateReplySink implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE SINK %s_replies AS
                SELECT
                   '200' AS status,
                   c.correlation_id
                FROM %s_commands c
                LEFT JOIN %s_events r
                ON c.correlation_id = r.correlation_id
            WITH (
                connector = 'kafka',
                topic = '%s.%s_replies',
                properties.bootstrap.server = '%s',
            ) FORMAT PLAIN ENCODE AVRO (
              force_append_only = 'true',
              schema.registry = '%s'
            );\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            final String name = command.name();
            final String schema = command.schema();

            final String systemName = "%s.%s".formatted(systemSchema, name);

            String sqlQuery = String.format(sqlFormat, systemName, systemName, systemName,
                schema, name, bootstrapServer, schemaRegistry);

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
            return errorState();
        }
    }

    private final class InsertIntoCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            INSERT INTO %s.%s (name, sql) VALUES ('%s', '%s'); FLUSH;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String newSql = sql.replace("\u0000", "");
            newSql = newSql.replaceAll("'", "''");
            String sqlQuery = String.format(sqlFormat, systemSchema, ZFUNCTION_NAME, name, newSql);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_ZFUNCTION_COMMAND);
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
