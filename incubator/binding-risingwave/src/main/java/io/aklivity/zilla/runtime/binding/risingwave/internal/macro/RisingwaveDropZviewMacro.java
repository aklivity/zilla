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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveDropZviewMacro
{
    private final String systemSchema;
    private final String sql;
    private final Drop command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveDropZviewMacro(
        String systemSchema,
        String sql,
        Drop command,
        RisingwaveMacroHandler handler)
    {
        this.systemSchema = systemSchema;
        this.sql = sql;
        this.command = command;
        this.handler = handler;
    }

    public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        DropTopicState state = new DropTopicState();
        state.onStarted(traceId, authorization);

        return state;
    }

    private final class DropTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP TOPIC %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name());
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropSinkState state = new DropSinkState();
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

    private final class DropSinkState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SINK %s.%s_sink;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DeleteFromCatalogState state = new DeleteFromCatalogState();
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

    private final class DeleteFromCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DELETE FROM %s.zviews WHERE name = '%s';\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropMaterializedViewState state = new DropMaterializedViewState();
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

    private final class DropMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP MATERIALIZED VIEW %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name());
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.DROP_ZVIEW_COMMAND);
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
