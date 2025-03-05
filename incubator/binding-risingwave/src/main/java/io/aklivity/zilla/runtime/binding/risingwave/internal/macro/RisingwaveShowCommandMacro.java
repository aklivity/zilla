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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public class RisingwaveShowCommandMacro extends RisingwaveMacroBase
{
    private static final int FLAGS_INIT = 0x02;

    private final Map<String, String> showCommandMappings;
    {
        Map<String, String> showCommandMappings = new Object2ObjectHashMap<>();
        showCommandMappings.put("tables", "ztables");
        showCommandMappings.put("materialized views", "zviews");
        this.showCommandMappings = showCommandMappings;
    }

    private final String sql;
    private final String command;
    private final RisingwaveMacroHandler handler;
    private final List<String> zcolumns;

    public RisingwaveShowCommandMacro(
        String sql,
        String command,
        RisingwaveMacroHandler handler)
    {
        super(sql, handler);

        this.sql = sql;
        this.command = command;
        this.handler = handler;
        this.zcolumns = new ArrayList<>();
    }


    public RisingwaveMacroState start()
    {
        return new SelectCommandState();
    }

    private final class SelectCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SELECT name FROM zb_catalog.%s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String zcommand = showCommandMappings.get(command.toLowerCase());
            String sqlQuery = String.format(sqlFormat, zcommand);

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
            int progress = offset;
            if ((flags & FLAGS_INIT) != 0x00)
            {
                progress += Short.BYTES;
            }

            int length = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
            progress += Integer.BYTES;

            if (length > 0)
            {
                String name = buffer.getStringWithoutLengthAscii(progress, length);
                zcolumns.add(name);
            }

            return this;
        }

        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            ShowCommandState state = new ShowCommandState();
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

    private final class ShowCommandState implements RisingwaveMacroState
    {
        private final String sqlFormat = """
            SHOW %s;\u0000""";

        @Override
        public void onStarted(
            long traceId,
            long authorization)
        {
            String sqlQuery = String.format(sqlFormat, command);

            handler.doExecuteSystemClient(traceId, authorization, sqlQuery);
        }

        @Override
        public RisingwaveMacroState onType(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doDescription(traceId, authorization, List.of("Name"));
            return this;
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
            int progress = offset;
            if ((flags & FLAGS_INIT) != 0x00)
            {
                progress += Short.BYTES;
            }

            int length = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
            progress += Integer.BYTES;

            if (length > 0 &&
                !zcolumns.contains(buffer.getStringWithoutLengthAscii(progress, length)))
            {
                handler.doColumn(client, traceId, authorization, flags, buffer, offset, limit);
            }

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
