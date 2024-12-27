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

import io.aklivity.zilla.runtime.binding.pgsql.parser.model.CreateZfunction;
import io.aklivity.zilla.runtime.binding.risingwave.internal.stream.RisingwaveCompletionCommand;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public interface RisingwaveMacroHandler
{
    void doExecuteUserClient(
        long traceId,
        long authorization,
        String query);

    void doExecuteSystemClient(
        long traceId,
        long authorization,
        String query);

    void doDescription(
        long traceId,
        long authorization,
        List<String> columns);

    <T> void doColumn(
        T client,
        long traceId,
        long authorization,
        int flags,
        DirectBuffer buffer,
        int offset,
        int limit);

    <T> void doZfunctionRow(
        T client,
        long traceId,
        long authorization,
        CreateZfunction command);

    void doCompletion(
        long traceId,
        long authorization,
        RisingwaveCompletionCommand command);

    void doReady(
        long traceId,
        long authorization,
        int progress);

    void doFlushProxy(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    <T> void doDataProxy(
        T client,
        long traceId,
        long authorization,
        int flags,
        DirectBuffer buffer,
        int offset,
        int length,
        OctetsFW extension);
}
