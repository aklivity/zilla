package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZview;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZviewMacro
{
    private final CreateZview command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveCreateZviewMacro(
        CreateZview command,
        RisingwaveMacroHandler handler)
    {
        this.command = command;
        this.handler = handler;
    }

    public RisingwaveMacroState start()
    {
        CreateMaterializedVievState state = new CreateMaterializedVievState();
        state.doExecute();

        return state;
    }

    private class CreateMaterializedVievState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS %s AS %s;\u0000""";

        private void doExecute()
        {
            String name = command.name();
            String select = command.select();

            String sqlQuery = String.format(sqlFormat, name, select);

            handler.doExecute(sqlQuery);
        }

        @Override
        public RisingwaveMacroState onRow()
        {
            return this;
        }

        @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            return null;
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            return this;
        }

        @Override
        public RisingwaveMacroState onError(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            return this;
        }
    }
}
