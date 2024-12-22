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
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.pgsql.parser.PgsqlParser;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZstream;
import io.aklivity.zilla.runtime.binding.pgsql.parser.model.ZstreamColumn;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZstreamMacro extends RisingwaveMacroBase
{

    private final List<CreateZfunction> functions; // <name, sql>
    private final StringBuilder fieldBuilder;
    private final StringBuilder includeBuilder;

    private final String bootstrapServer;
    private final String schemaRegistry;
    private final PgsqlParser parser;

    private final long scanStartupMil;
    private final String systemSchema;
    private final String user;
    private final CreateZstream command;

    private Iterator<CreateZfunction> zfunctionInterator;

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
        this.fieldBuilder = new StringBuilder();
        this.includeBuilder = new StringBuilder();
    }

    public RisingwaveMacroState start()
    {
        return new SelectZfunctionsCommandState();
    }

    private final class SelectZfunctionsCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SELECT * FROM zb_catalog.zfunctions; WHERE name IN (%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String handlers = command.commandHandlers().keySet().stream()
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
            CREATE TOPIC IF NOT EXISTS %s_commands (%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            fieldBuilder.setLength(0);

            CreateZfunction zfunction = functions.getFirst();

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
            CREATE SOURCE IF NOT EXISTS %s (*)
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

            includeBuilder.setLength(0);

            command.columns().stream()
                .filter(c -> ZILLA_MAPPINGS.containsKey(c.generatedAlways()))
                .forEach(c ->
                {
                    if (ZILLA_TIMESTAMP.equals(c.generatedAlways()))
                    {
                        includeBuilder.append(String.format(ZILLA_MAPPINGS.get(c.generatedAlways()), c.name()));
                    }
                    else
                    {
                        includeBuilder.append(String.format(ZILLA_MAPPINGS.get(c), c.name()));
                    }
                    includeBuilder.append("\n");
                });

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
        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            if (zfunctionInterator == null)
            {
                zfunctionInterator = functions.iterator();
            }

            CreateZfunction zfunction = functions.iterator().next();


            handler.doExecuteSystemClient(traceId, authorization, sql);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            RisingwaveMacroState state = zfunctionInterator.hasNext()
                ? new CreateMaterializedViewState()
                : new GrantResourceState();
            state.onStarted(traceId, authorization);

            return state;
        }
    }

    private final class GrantResourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            GRANT ALL PRIVILEGES ON SOURCE %s.%s TO %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.schema(), command.name(), user);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_STREAM_COMMAND);
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
