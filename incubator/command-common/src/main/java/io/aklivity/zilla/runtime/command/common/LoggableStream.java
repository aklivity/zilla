/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.command.common;

import java.util.function.LongPredicate;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.command.common.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.common.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.command.common.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.common.spy.RingBufferSpy;
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
        Predicate<String> hasFrameType,
        LongPredicate nextTimestamp,
        Handlers handlers)
    {
        this.index = index;
        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.nextTimestamp = nextTimestamp;

        final Int2ObjectHashMap<MessageConsumer> frameHandlers = new Int2ObjectHashMap<>();

        if (hasFrameType.test("BEGIN"))
        {
            frameHandlers.put(BeginFW.TYPE_ID, (t, b, i, l) -> handlers.onBegin(beginRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("DATA"))
        {
            frameHandlers.put(DataFW.TYPE_ID, (t, b, i, l) -> handlers.onData(dataRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("END"))
        {
            frameHandlers.put(EndFW.TYPE_ID, (t, b, i, l) -> handlers.onEnd(endRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("ABORT"))
        {
            frameHandlers.put(AbortFW.TYPE_ID, (t, b, i, l) -> handlers.onAbort(abortRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("WINDOW"))
        {
            frameHandlers.put(WindowFW.TYPE_ID, (t, b, i, l) -> handlers.onWindow(windowRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("RESET"))
        {
            frameHandlers.put(ResetFW.TYPE_ID, (t, b, i, l) -> handlers.onReset(resetRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("CHALLENGE"))
        {
            frameHandlers.put(ChallengeFW.TYPE_ID, (t, b, i, l) -> handlers.onChallenge(challengeRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("SIGNAL"))
        {
            frameHandlers.put(SignalFW.TYPE_ID, (t, b, i, l) -> handlers.onSignal(signalRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("FLUSH"))
        {
            frameHandlers.put(FlushFW.TYPE_ID, (t, b, i, l) -> handlers.onFlush(flushRO.wrap(b, i, i + l)));
        }
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
