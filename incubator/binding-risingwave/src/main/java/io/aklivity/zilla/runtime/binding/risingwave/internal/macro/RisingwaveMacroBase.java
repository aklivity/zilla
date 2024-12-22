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

import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public abstract class RisingwaveMacroBase
{
    protected static final String ZILLA_IDENTITY = "GENERATED ALWAYS AS IDENTITY";
    protected static final String ZILLA_TIMESTAMP = "GENERATED ALWAYS AS NOW";

    protected static final Map<String, String> ZILLA_MAPPINGS = new Object2ObjectHashMap<>();
    static
    {
        ZILLA_MAPPINGS.put(ZILLA_IDENTITY, "INCLUDE header 'zilla:identity' AS %s_header\n");
        ZILLA_MAPPINGS.put(ZILLA_TIMESTAMP, "INCLUDE timestamp AS %s_timestamp\n");
    }

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
