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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveCreateZfunctionMacro extends RisingwaveMacroBase
{
    private static final String ZFUNCTION_NAME = "zfunctions";

    private final String systemSchema;
    private final String user;
    private final String sql;
    private final CreateZfunction command;
    private final RisingwaveMacroHandler handler;

    public RisingwaveCreateZfunctionMacro(
        String systemSchema,
        String user,
        String sql,
        CreateZfunction command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

        this.systemSchema = systemSchema;
        this.user = user;
        this.sql = sql;
        this.command = command;
        this.handler = handler;
    }

    public RisingwaveMacroState start()
    {
        return new InsertIntoCatalogState();
    }


    private final class InsertIntoCatalogState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            INSERT INTO %s.%s (name, sql) VALUES ('%s', '%s');\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String name = command.name();

            String newSql = sql.replace("\u0000", "");
            newSql = newSql.replaceAll("'", "''");
            String sqlQuery = String.format(sqlFormat, systemSchema, ZFUNCTION_NAME, name, newSql);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            FlushState state = new FlushState();
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

    private final class FlushState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            FLUSH;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            handler.doExecuteSystemClient(traceId, authorization, sqlFormat);
        }

        @Override
        public RisingwaveMacroState onCompletion(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doCompletion(traceId, authorization, RisingwaveCompletionCommand.CREATE_ZFUNCTION_COMMAND);
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
