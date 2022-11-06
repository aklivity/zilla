/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import java.util.function.LongPredicate;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.command.dump.internal.airline.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.dump.internal.airline.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.dump.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class LoggableStream implements AutoCloseable
{
    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final FlushFW flushRO = new FlushFW();
    private final StreamsLayout layout;
    private final RingBufferSpy streamsBuffer;
    private final LongPredicate nextTimestamp;
    private final int index;

    private final Int2ObjectHashMap<MessageConsumer> frameHandlers;

    public LoggableStream(
        int index,
        StreamsLayout layout,
        LongPredicate nextTimestamp,
        Handlers handlers)
    {
        this.index = index;
        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.nextTimestamp = nextTimestamp;

        final Int2ObjectHashMap<MessageConsumer> frameHandlers = new Int2ObjectHashMap<>();

        frameHandlers.put(BeginFW.TYPE_ID, (t, b, i, l) -> handlers.onBegin(beginRO.wrap(b, i, i + l)));
        frameHandlers.put(DataFW.TYPE_ID, (t, b, i, l) -> handlers.onData(dataRO.wrap(b, i, i + l)));
        frameHandlers.put(EndFW.TYPE_ID, (t, b, i, l) -> handlers.onEnd(endRO.wrap(b, i, i + l)));
        frameHandlers.put(AbortFW.TYPE_ID, (t, b, i, l) -> handlers.onAbort(abortRO.wrap(b, i, i + l)));
        frameHandlers.put(WindowFW.TYPE_ID, (t, b, i, l) -> handlers.onWindow(windowRO.wrap(b, i, i + l)));
        frameHandlers.put(ResetFW.TYPE_ID, (t, b, i, l) -> handlers.onReset(resetRO.wrap(b, i, i + l)));
        frameHandlers.put(ChallengeFW.TYPE_ID, (t, b, i, l) -> handlers.onChallenge(challengeRO.wrap(b, i, i + l)));
        frameHandlers.put(SignalFW.TYPE_ID, (t, b, i, l) -> handlers.onSignal(signalRO.wrap(b, i, i + l)));
        frameHandlers.put(FlushFW.TYPE_ID, (t, b, i, l) -> handlers.onFlush(flushRO.wrap(b, i, i + l)));
        this.frameHandlers = frameHandlers;
    }

    public int process()
    {
        return streamsBuffer.spy(this::handleFrame, 1);
    }

    @Override
    public void close() throws Exception
    {
        layout.close();
    }

    @Override
    public String toString()
    {
        return String.format("data%d (spy)", index);
    }

    private boolean handleFrame(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long timestamp = frame.timestamp();

        if (!nextTimestamp.test(timestamp))
        {
            return false;
        }

        final MessageConsumer handler = frameHandlers.get(msgTypeId);
        if (handler != null)
        {
            handler.accept(msgTypeId, buffer, index, length);
        }

        return true;
    }
}
