package io.aklivity.zilla.runtime.binding.risingwave.internal.macro;

public interface RisingwaveMacroHandler
{
    void doExecute(
        String query);

    void doRow();

    void doCompletion();

    void doReady(
        long traceId,
        long authorization);
}
