package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateStream;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveUnknownMacro
{
    private final String systemSchema;
    private final String user;
    private final String sql;
    private final RisingwaveMacroHandler handler;

    public RisingwaveUnknownMacro(
        String systemSchema,
        String user,
        String sql,
        RisingwaveMacroHandler handler)
    {
        this.systemSchema = systemSchema;
        this.user = user;
        this.sql = sql;
        this.handler = handler;
    }


    public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        UnknownState state = new UnknownState();
        state.doExecute(traceId, authorization);

        return state;
    }

    private class UnknownState implements RisingwaveMacroState
    {
        private void doExecute(
            long traceId,
            long authorization)
        {
            handler.doExecute(traceId, authorization, sql);
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
