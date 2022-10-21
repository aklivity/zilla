package io.aklivity.zilla.runtime.command.common;

import io.aklivity.zilla.runtime.command.common.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.WindowFW;

public interface Handlers
{
    void onBegin(final BeginFW begin);
    void onData(final DataFW data);
    void onEnd(final EndFW end);
    void onAbort(final AbortFW abort);
    void onReset(final ResetFW reset);
    void onWindow(final WindowFW window);
    void onSignal(final SignalFW signal);
    void onChallenge(final ChallengeFW challenge);
    void onFlush(final FlushFW flush);
}
