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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.Alter;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveAlterZtableMacro extends RisingwaveMacroBase
{
    private final StringBuilder fieldBuilder;
    private final Alter command;

    public RisingwaveAlterZtableMacro(
        String sql,
        Alter command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

        this.command = command;
        this.fieldBuilder = new StringBuilder();
    }

    public RisingwaveMacroState start()
    {
        return new AlterTopicState();
    }

    private final class AlterTopicState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            ALTER TOPIC %s %s;\u0000""";
        private final String fieldFormat = "%s COLUMN %s %s, ";

        @Override
        public void onStarted(
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
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            AlterTableState state = new AlterTableState();
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

    private final class AlterTableState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            ALTER TABLE %s %s;\u0000""";
        private final String fieldFormat = "%s COLUMN %s %s, ";

        @Override
        public void onStarted(
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
            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.ALTER_ZTABLE_COMMAND);
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
