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

import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public interface RisingwaveMacroHandler
{
    void doExecute(
        long traceId,
        long authorization,
        String query);

    void doDescription(
        long traceId,
        long authorization,
        String name);

    <T> void doRow(
        T client,
        long traceId,
        long authorization,
        int flags,
        DirectBuffer buffer,
        int offset,
        int limit);

    void doCompletion(
        long traceId,
        long authorization,
        RisingwaveCompletionCommand command);

    void doError(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    void doReady(
        long traceId,
        long authorization,
        int progress);
}
