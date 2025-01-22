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

public class RisingwaveDropZfunctionMacro extends RisingwaveMacroBase
{
    private final String systemSchema;
    private final Drop command;

    public RisingwaveDropZfunctionMacro(
        String systemSchema,
        String sql,
        Drop command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

        this.systemSchema = systemSchema;
        this.command = command;
    }

    public RisingwaveMacroState start()
    {
        return new DeleteCatalogState();
    }

    private final class DeleteCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DELETE FROM %s.zfunctions WHERE name = '%s';\u0000""";

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
            DropReplySink state = new DropReplySink();
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

    private final class DropReplySink implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SINK %s.%s_replies;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String sqlQuery = String.format(sqlFormat, systemSchema, name);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropSinkTopicState state = new DropSinkTopicState();
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

    private final class DropSinkTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP TOPIC %s.%s_replies;\u0000""";

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
            DropReplySinkInto state = new DropReplySinkInto();
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

    private final class DropReplySinkInto implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SINK %s.%s_sink_into;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String sqlQuery = String.format(sqlFormat, systemSchema, name);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropReplyMaterializedViewState state = new DropReplyMaterializedViewState();
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

    private final class DropReplyMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP MATERIALIZED VIEW %s.%s_events;\u0000""";

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
            DropSourceState state = new DropSourceState();
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

    private final class DropSourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
           DROP SOURCE %s.%s_commands;\u0000""";

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
            DropSourceTopicState state = new DropSourceTopicState();
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

    private final class DropSourceTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP TOPIC %s.%s_commands;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.schema(), command.name());

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.DROP_ZFUNCTION_COMMAND);
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
