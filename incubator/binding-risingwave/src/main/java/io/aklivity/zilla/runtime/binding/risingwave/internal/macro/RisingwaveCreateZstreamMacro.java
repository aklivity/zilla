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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PgsqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZstream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Select;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZstreamMacro extends RisingwaveMacroBase
{
    private static final String ZSTREAM_NAME = "zstreams";

    private static final Map<String, String> ZILLA_MAPPINGS = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS.put(ZILLA_IDENTITY, "INCLUDE header 'zilla:identity' VARCHAR AS %s");
        ZILLA_MAPPINGS.put(ZILLA_TIMESTAMP, "INCLUDE timestamp AS %s");
    }

    private final List<CreateZfunction> functions; // <name, sql>

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final PgsqlParser parser;

    private final long scanStartupMil;
    private final String systemSchema;
    private final String user;
    private final CreateZstream command;

    private Iterator<CreateZfunction> zfunctionInterator;
    private CreateZfunction lastZfunction;

    public RisingwaveCreateZstreamMacro(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil,
        String systemSchema,
        String user,
        String sql,
        CreateZstream command,
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

        this.functions = new ArrayList<>();
    }

    public RisingwaveMacroState start()
    {
        return new SelectZfunctionsCommandState();
    }

    private final class SelectZfunctionsCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SELECT * FROM zb_catalog.zfunctions WHERE name IN (%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String handlers = command.commandHandlers().values().stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
            String sqlQuery = String.format(sqlFormat, handlers);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public <T> RisingwaveMacroState onRow(
            T client,
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension)
        {
            int progress = offset;

            short fields = buffer.getShort(progress, ByteOrder.BIG_ENDIAN);
            progress += Short.BYTES;

            assert fields == 2;

            int nameLength = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
            progress += Integer.BYTES + nameLength;

            int sqlLength = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
            progress += Integer.BYTES;

            String statement = buffer.getStringWithoutLengthAscii(progress, sqlLength);

            CreateZfunction createZfunction = parser.parseCreateZfunction(statement);

            functions.add(createZfunction);

            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateSourceTopicState state = new CreateSourceTopicState();
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

    private final class CreateSourceTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC %s_commands (%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            CreateZfunction zfunction = functions.stream().findFirst().get();

            String arguments =  zfunction.arguments().stream()
                .map(arg -> arg.name() + " " + arg.type())
                .collect(Collectors.joining(", "));
            String fields = "%s VARCHAR, %s".formatted(command.dispatchOn(), arguments);

            String sqlQuery = String.format(sqlFormat, topic, fields);

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
            CREATE SOURCE %s_commands (*)
            INCLUDE header 'zilla:correlation-id' VARCHAR AS correlation_id
            %s
            WITH (
               connector='kafka',
               properties.bootstrap.server='%s',
               topic='%s.%s_commands',
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

            String include = command.columns().stream()
                .filter(c -> ZILLA_MAPPINGS.containsKey(c.generatedAlways()))
                .map(c ->
                {
                    String name = c.name();
                    String generated = c.generatedAlways();
                    return String.format(ZILLA_MAPPINGS.get(generated), name);
                })
                .collect(Collectors.joining("\n"));

            String sqlQuery = String.format(sqlFormat, table, include, bootstrapServer,
                schema, table, scanStartupMil, schemaRegistry);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateHandlerMaterializedViewState state = new CreateHandlerMaterializedViewState();
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

    private final class CreateHandlerMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW %s AS
                SELECT
                    c.correlation_id,
                    %s
                FROM %s_commands c
                %s
                WHERE %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            if (lastZfunction == null)
            {
                if (zfunctionInterator == null)
                {
                    zfunctionInterator = functions.iterator();
                }

                lastZfunction = zfunctionInterator.next();
                Select select = lastZfunction.select();

                String columns =  select.columns().stream()
                    .map(c -> c.replace("args", "c"))
                    .collect(Collectors.joining(", "));

                String name = lastZfunction.name();
                String from = command.name();

                String join = "";
                if (select.from() != null)
                {
                    join = "JOIN %s".formatted(select.from());

                    if (select.whereClause() != null)
                    {
                        join = String.format("%s ON %s", join, select.whereClause().replace("args", "c"));
                    }
                }

                String commandName = command.commandHandlers().entrySet().stream()
                    .filter(e -> e.getValue().equals(name))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .get();

                String where = "c.%s = '%s'".formatted(command.dispatchOn(), commandName);

                String sqlQuery = String.format(sqlFormat, name, columns, from, join, where);

                handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
            }
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            lastZfunction = null;

            RisingwaveMacroState state = zfunctionInterator.hasNext()
                ? new CreateHandlerMaterializedViewState()
                : new CreateStreamMaterializedViewState();
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

    private final class CreateStreamMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW %s AS
                %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String selects =  command.commandHandlers().values().stream()
                .map("SELECT * FROM %s"::formatted)
                .collect(Collectors.joining("UNION ALL\n"));

            String sqlQuery = String.format(sqlFormat, command.name(), selects);

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
            GRANT ALL PRIVILEGES ON MATERIALIZED VIEW %s.%s TO %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.schema(), command.name(), user);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            CreateReplyMaterializedViewState state = new CreateReplyMaterializedViewState();
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

    private final class CreateReplyMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW %s_reply_handler AS
                SELECT
                    '200' AS status,
                    correlation_id
                FROM %s.%s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name(), command.schema(), command.name());

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
            CREATE TOPIC %s_replies_sink (status VARCHAR, correlation_id VARCHAR);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            CreateZfunction zfunction = functions.stream().findFirst().get();

            String arguments =  zfunction.arguments().stream()
                .map(arg -> arg.name() + " " + arg.type())
                .collect(Collectors.joining(", "));

            String sqlQuery = String.format(sqlFormat, topic, arguments);

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
            CREATE SINK %s_replies_sink AS
                SELECT
                      COALESCE(r.status, '400') AS status,
                      c.correlation_id
                  FROM %s_commands c
                  LEFT JOIN %s_reply_handler r
                  ON c.correlation_id = r.correlation_id
            WITH (
                connector = 'kafka',
                topic = '%s.%s_replies',
                properties.bootstrap.server = '%s',
            ) FORMAT PLAIN ENCODE AVRO (
              force_append_only='true',
              schema.registry = '%s'
            );\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();
            String schema = command.schema();

            String sqlQuery = String.format(sqlFormat, name, name, name, schema, name, bootstrapServer, schemaRegistry);

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
            INSERT INTO %s.%s (name, sql) VALUES ('%s', '%s');\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String newSql = sql.replace("\u0000", "");
            newSql = newSql.replaceAll("'", "''");
            String sqlQuery = String.format(sqlFormat, systemSchema, ZSTREAM_NAME, name, newSql);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_ZSTREAM_COMMAND);
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
