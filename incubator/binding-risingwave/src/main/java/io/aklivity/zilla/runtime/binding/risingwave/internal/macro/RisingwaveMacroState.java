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

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public interface RisingwaveMacroState
{
    default <T> RisingwaveMacroState onRow(
        T client,
        long traceId,
        long authorization,
        int flags,
        DirectBuffer buffer,
        int offset,
        int limit,
        OctetsFW extension)
    {
        return this;
    }

    default RisingwaveMacroState onType(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx)
    {
        return this;
    }

    default RisingwaveMacroState onCompletion(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx)
    {
        return this;
    }

    RisingwaveMacroState onReady(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    RisingwaveMacroState onError(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);
}
