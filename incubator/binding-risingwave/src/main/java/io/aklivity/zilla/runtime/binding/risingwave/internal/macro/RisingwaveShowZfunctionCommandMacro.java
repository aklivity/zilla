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

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveShowZfunctionCommandMacro
{
    private final String sql;
    private final String command;
    private final RisingwaveMacroHandler handler;

    private final List<String> columns = List.of("Name", "Arguments", "Return Type", "Language", "Link");

    public RisingwaveShowZfunctionCommandMacro(
        String sql,
        String command,
        RisingwaveMacroHandler handler)
    {
        this.sql = sql;
        this.command = command;
        this.handler = handler;
    }

    public RisingwaveMacroState start()
    {
        return new ShowCommandState();
    }

    private final class ShowCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SELECT * FROM zb_catalog.zfunctions;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command.toLowerCase());

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
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
            handler.doColumn(client, traceId, authorization, flags, buffer, offset, limit);
            return this;
        }

        @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doDescription(traceId, authorization, columns);
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
