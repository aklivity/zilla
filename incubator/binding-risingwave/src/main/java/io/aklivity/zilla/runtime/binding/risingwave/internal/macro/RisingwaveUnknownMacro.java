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

    private final class UnknownState implements RisingwaveMacroState
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
            return null;
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
