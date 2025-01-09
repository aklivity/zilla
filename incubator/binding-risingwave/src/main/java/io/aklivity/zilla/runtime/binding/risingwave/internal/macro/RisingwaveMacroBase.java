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

public abstract class RisingwaveMacroBase
{
    protected static final String ZILLA_IDENTITY = "GENERATED ALWAYS AS IDENTITY";
    protected static final String ZILLA_TIMESTAMP = "GENERATED ALWAYS AS NOW";

    protected final RisingwaveMacroHandler handler;
    protected final String sql;

    public RisingwaveMacroBase(
        String sql,
        RisingwaveMacroHandler handler)
    {
        this.sql = sql;
        this.handler = handler;
    }

    protected RisingwaveMacroState errorState()
    {
        return new ErrorState();
    }

    protected final class ErrorState implements RisingwaveMacroState
    {
        @Override
        public RisingwaveMacroState onReady(
            long traceId,
            long authorization,
            PgsqlFlushExFW flushEx)
        {
            handler.doReady(traceId, authorization, sql.length());
            return null;
        }
    }
}
