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
