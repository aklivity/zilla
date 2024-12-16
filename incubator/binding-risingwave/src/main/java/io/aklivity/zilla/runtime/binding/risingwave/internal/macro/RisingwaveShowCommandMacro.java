package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.String32FW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFormat;

public class RisingwaveShowCommandMacro
{
    private final String systemSchema;
    private final String user;
    private final String sql;
    private final String command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveShowCommandMacro(
        String systemSchema,
        String user,
        String sql,
        String command,
        RisingwaveMacroHandler handler)
    {
        this.systemSchema = systemSchema;
        this.user = user;
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
        public RisingwaveMacroState onRow(
            long traceId,
            long authorization,
            long routedId,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            OctetsFW extension)
        {
            String32FW column = columnRO.tryWrap(buffer, progress, limit);

            if (column != null)
            {
                PgsqlDataExFW dataEx = dataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(pgsqlTypeId)
                    .row(q -> q.deferred(0))
                    .build();

                final int length = column.sizeof();

                int statementProgress = 0;
                statementBuffer.putShort(statementProgress, (short) 1, ByteOrder.BIG_ENDIAN);
                statementProgress += Short.BYTES;
                statementBuffer.putBytes(statementProgress, column.buffer(), column.offset(), length);
                statementProgress += length;

                server.doAppData(client, routedId, traceId, authorization, flags,
                    statementBuffer, 0, statementProgress, dataEx);
            }
        }

        @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            PgsqlFlushExFW descriptionEx = flushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(pgsqlTypeId)
                .type(t -> t
                    .columns(c -> c
                        .item(s -> s
                            .name("Name\u0000")
                            .tableOid(0)
                            .index((short) 0)
                            .typeOid(701)
                            .length((short) 4)
                            .modifier(-1)
                            .format(f -> f.set(PgsqlFormat.TEXT))
                        )))
                .build();

            handler.doAppFlush(traceId, authorization, descriptionEx);

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
