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

import java.util.LinkedHashMap;
import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateStream;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateStreamMacro
{
    //TODO: Remove after implementing zstream
    private static final String ZILLA_CORRELATION_ID_OLD = "zilla_correlation_id";
    private static final String ZILLA_IDENTITY_OLD = "zilla_identity";
    private static final String ZILLA_TIMESTAMP_OLD = "zilla_timestamp";

    private static final Map<String, String> ZILLA_MAPPINGS_OLD = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS_OLD.put(ZILLA_CORRELATION_ID_OLD, "INCLUDE header 'zilla:correlation-id' AS %s\n");
        ZILLA_MAPPINGS_OLD.put(ZILLA_IDENTITY_OLD, "INCLUDE header 'zilla:identity' AS %s\n");
        ZILLA_MAPPINGS_OLD.put(ZILLA_TIMESTAMP_OLD, "INCLUDE timestamp AS %s\n");
    }

    protected final StringBuilder fieldBuilder = new StringBuilder();
    protected final StringBuilder includeBuilder = new StringBuilder();

    private final String bootstrapServer;
    private final String schemaRegistry;

    private final long scanStartupMil;
    private final String systemSchema;
    private final String user;
    private final String sql;
    private final CreateStream command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveCreateStreamMacro(
        String bootstrapServer,
        String schemaRegistry,
        long scanStartupMil,
        String systemSchema,
        String user,
        String sql,
        CreateStream command,
        RisingwaveMacroHandler handler)
    {
        this.scanStartupMil = scanStartupMil;
        this.systemSchema = systemSchema;
        this.user = user;
        this.sql = sql;
        this.command = command;
        this.handler = handler;

        this.bootstrapServer = bootstrapServer;
        this.schemaRegistry = schemaRegistry;
    }

    public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        CreateTopicState state = new CreateTopicState();
        state.onStarted(traceId, authorization);

        return state;
    }

    private final class CreateTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE TOPIC IF NOT EXISTS %s (%s%s);\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String topic = command.name();

            fieldBuilder.setLength(0);

            command.columns()
                .entrySet()
                .stream()
                .filter(e -> !ZILLA_MAPPINGS_OLD.containsKey(e.getKey()))
                .forEach(e -> fieldBuilder.append(
                    String.format("%s %s, ", e.getKey(), e.getValue())));

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder, "");

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
            return this;
        }
    }

    private final class CreateSourceState implements RisingwaveMacroState
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

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String schema = command.schema();
            String table = command.name();

            includeBuilder.setLength(0);
            Map<String, String> includes = command.columns().entrySet().stream()
                .filter(e -> ZILLA_MAPPINGS_OLD.containsKey(e.getKey()))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

            if (!includes.isEmpty())
            {
                includeBuilder.append("\n");
                includes.forEach((k, v) -> includeBuilder.append(String.format(ZILLA_MAPPINGS_OLD.get(k), k)));
                includeBuilder.delete(includeBuilder.length() - 1, includeBuilder.length());
            }

            String sqlQuery = String.format(sqlFormat, table, includeBuilder, bootstrapServer, schema,
                table, scanStartupMil, schemaRegistry);
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
