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
