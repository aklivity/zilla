package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

import io.aklivity.zilla.runtime.binding.risingwave.internal.types.stream.PgsqlFlushExFW;

public interface RisingwaveMacroState
{
    RisingwaveMacroState onRow();

    RisingwaveMacroState onType(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    RisingwaveMacroState onCompletion(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    RisingwaveMacroState onReady(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);

    RisingwaveMacroState onError(
        long traceId,
        long authorization,
        PgsqlFlushExFW flushEx);
}
