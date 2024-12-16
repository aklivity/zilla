package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveAlterZtableMacro
{
    private final StringBuilder fieldBuilder;
    private final String sql;
    private final Alter command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveAlterZtableMacro(
        String sql,
        Alter command,
        RisingwaveMacroHandler handler)
    {
        this.sql = sql;
        this.command = command;
        this.handler = handler;
        this.fieldBuilder = new StringBuilder();
    }

     public RisingwaveMacroState start(
        long traceId,
        long authorization)
    {
        AlterTopicState state = new AlterTopicState();
        state.doExecute(traceId, authorization);

        return state;
    }

    private class AlterTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            ALTER TOPIC %s %s;\u0000""";
        private final String fieldFormat = "%s COLUMN %s %s, ";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String topic = command.name();
            fieldBuilder.setLength(0);

            command.expressions()
                .forEach(c -> fieldBuilder.append(
                    String.format(fieldFormat, c.operation().name(), c.columnName(), c.columnType())));

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder);
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            AlterTableState state = new AlterTableState();
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

    private class AlterTableState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            ALTER TABLE %s %s;\u0000""";
        private final String fieldFormat = "%s COLUMN %s %s, ";

        private void doExecute(
            long traceId,
            long authorization)
        {
            String topic = command.name();
            fieldBuilder.setLength(0);

            command.expressions()
                .forEach(c -> fieldBuilder.append(
                    String.format(fieldFormat, c.operation().name(), c.columnName(), c.columnType())));

            fieldBuilder.delete(fieldBuilder.length() - 2, fieldBuilder.length());

            String sqlQuery = String.format(sqlFormat, topic, fieldBuilder);
            handler.doExecute(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.ALTER_STREAM_COMMAND);
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
