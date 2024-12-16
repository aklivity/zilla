package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFormat;

public class RisingwaveShowCommandMacro
{
    private final String sql;
    private final String command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveShowCommandMacro(
        String sql,
        String command,
        RisingwaveMacroHandler handler)
    {
        this.sql = sql;
        this.command = command;
        this.handler = handler;
    }


    public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        ShowCommandState state = new ShowCommandState();
        state.doExecute(traceId, authorization);

        return state;
    }

    private class ShowCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SELECT * FROM zb_catalog.%s;\u0000""";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.toLowerCase());

            handler.doExecute(traceId, authorization, sqlQuery);
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
            handler.doRow(client, traceId, authorization, flags, buffer, offset, limit);
            return this;
        }

        @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doDescription(traceId, authorization, "Name");
            return this;
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.SHOW_COMMAND);
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
