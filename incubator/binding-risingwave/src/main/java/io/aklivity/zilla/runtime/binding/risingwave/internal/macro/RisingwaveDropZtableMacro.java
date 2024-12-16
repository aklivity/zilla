package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Drop;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveDropZtableMacro
{
    private final String systemSchema;
    private final String sql;
    private final Drop command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveDropZtableMacro(
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
        state.doExecute(traceId, authorization);

        return state;
    }

    private class DropTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP TOPIC %s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropSinkState state = new DropSinkState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DropSinkState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SINK %s.%s_sink;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropTableSinkState state = new DropTableSinkState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DropTableSinkState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SINK %s.%s_view_sink;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropTableState state = new DropTableState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DropTableState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP TABLE %s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DeleteFromCatalogState state = new DeleteFromCatalogState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DeleteFromCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DELETE FROM %s.ztables WHERE name = '%s';\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, systemSchema, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropMaterializedViewState state = new DropMaterializedViewState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DropMaterializedViewState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP MATERIALIZED VIEW %s_view;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            DropSourceState state = new DropSourceState();
            state.doExecute(traceId, authorization);

            return state;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

    private class DropSourceState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            DROP SOURCE %s_source;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.name());
            handler.doExecute(traceId, authorization, sqlQuery);
        }

         @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.DROP_ZTABLE_COMMAND);
            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doReady(traceId, authorization, sql.length());
            return this;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doError(traceId, authorization, flushEx);
            return this;
        }
    }

}
