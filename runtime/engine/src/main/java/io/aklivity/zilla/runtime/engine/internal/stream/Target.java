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
package io.aklivity.zilla.runtime.engine.internal.stream;

import static io.aklivity.zilla.runtime.engine.config.MetricHandlerKind.ORIGIN;
import static io.aklivity.zilla.runtime.engine.config.MetricHandlerKind.ROUTED;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.instanceId;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.isInitial;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.streamIndex;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.throttleId;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.throttleIndex;
import static io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW.FIELD_OFFSET_TIMESTAMP;

import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessagePredicate;
import io.aklivity.zilla.runtime.engine.config.MetricHandlerKind;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.engine.internal.load.LoadEntry;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.engine.util.function.ObjectLongFunction;

public final class Target implements AutoCloseable
{
    private final FrameFW frameRO = new FrameFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final int localIndex;
    private final String targetName;
    private final AutoCloseable streamsLayout;
    private final MutableDirectBuffer writeBuffer;
    private final boolean timestamps;
    private final Long2ObjectHashMap<MessageConsumer> correlations;
    private final Int2ObjectHashMap<MessageConsumer>[] streams;
    private final Long2ObjectHashMap<LongHashSet> streamSets;
    private final Int2ObjectHashMap<MessageConsumer>[] throttles;
    private final MessageConsumer writeHandler;
    private final LongFunction<LoadEntry> supplyLoadEntry;
    private final ObjectLongFunction<MetricHandlerKind, MetricHandler> supplyMetricRecorder;

    private MessagePredicate streamsBuffer;


    public Target(
        EngineConfiguration config,
        int index,
        MutableDirectBuffer writeBuffer,
        Long2ObjectHashMap<MessageConsumer> correlations,
        Int2ObjectHashMap<MessageConsumer>[] streams,
        Long2ObjectHashMap<LongHashSet> streamSets,
        Int2ObjectHashMap<MessageConsumer>[] throttles,
        LongFunction<LoadEntry> supplyLoadEntry,
        ObjectLongFunction<MetricHandlerKind, MetricHandler> supplyMetricRecorder)
    {
        this.timestamps = config.timestamps();
        this.localIndex = index;

        final String targetName = String.format("data%d", index);
        this.targetName = targetName;

        final StreamsLayout streamsLayout = new StreamsLayout.Builder()
                .path(config.directory().resolve(targetName))
                .streamsCapacity(config.streamsBufferCapacity())
                .readonly(true)
                .build();
        this.streamsLayout = streamsLayout;
        this.streamsBuffer = streamsLayout.streamsBuffer()::write;

        this.writeBuffer = writeBuffer;
        this.supplyLoadEntry = supplyLoadEntry;
        this.supplyMetricRecorder = supplyMetricRecorder;
        this.correlations = correlations;
        this.streams = streams;
        this.streamSets = streamSets;
        this.throttles = throttles;

        this.writeHandler = this::handleWrite;
    }

    public void detach()
    {
        streamsBuffer = (t, b, i, l) -> true;
    }

    @Override
    public void close() throws Exception
    {
        for (int remoteIndex = 0; remoteIndex < throttles.length; remoteIndex++)
        {
            final int remoteIndex0 = remoteIndex;
            throttles[remoteIndex].forEach((id, handler) -> doSyntheticReset(throttleId(localIndex, remoteIndex0, id), handler));
        }

        streamsLayout.close();
    }

    @Override
    public String toString()
    {
        return String.format("%s (write)", targetName);
    }

    public MessageConsumer writeHandler()
    {
        return writeHandler;
    }

    private void handleWrite(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean handled = false;

        if (timestamps)
        {
            ((MutableDirectBuffer) buffer).putLong(index + FIELD_OFFSET_TIMESTAMP, System.nanoTime());
        }

        final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long originId = frame.originId();
        final long routedId = frame.routedId();
        final long streamId = frame.streamId();

        if (streamId == 0L)
        {
            handled = handleWriteSystem(originId, routedId, streamId, msgTypeId, buffer, index, length);
        }
        else if (isInitial(streamId))
        {
            handled = handleWriteInitial(originId, routedId, streamId, msgTypeId, buffer, index, length);
        }
        else
        {
            handled = handleWriteReply(originId, routedId, streamId, msgTypeId, buffer, index, length);
        }

        if (!handled)
        {
            throw new IllegalStateException("Unable to write to streams buffer");
        }
    }

    private boolean handleWriteSystem(
        long originId,
        long routedId,
        long streamId,
        int msgTypeId,
        DirectBuffer buffer,
        int index, int length)
    {
        boolean handled = false;

        switch (msgTypeId)
        {
        case FlushFW.TYPE_ID:
            handled = streamsBuffer.test(msgTypeId, buffer, index, length);
            break;
        case WindowFW.TYPE_ID:
            handled = streamsBuffer.test(msgTypeId, buffer, index, length);
            break;
        }

        return handled;
    }

    private boolean handleWriteInitial(
        long originId,
        long routedId,
        long streamId,
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean handled;

        if ((msgTypeId & 0x4000_0000) == 0)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case DataFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case EndFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                throttles[throttleIndex(streamId)].remove(instanceId(streamId));
                break;
            case AbortFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                throttles[throttleIndex(streamId)].remove(instanceId(streamId));
                break;
            case FlushFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            default:
                handled = true;
                break;
            }
        }
        else
        {
            supplyMetricRecorder.apply(ORIGIN, originId).onEvent(msgTypeId, buffer, index, length);
            supplyMetricRecorder.apply(ROUTED, routedId).onEvent(msgTypeId, buffer, index, length);
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                streams[streamIndex(streamId)].remove(instanceId(streamId));
                supplyLoadEntry.apply(routedId).initialClosed(1L).initialErrored(1L);
                LongHashSet streamIdSet = streamSets.get(routedId);
                if (streamIdSet != null)
                {
                    streamIdSet.remove(streamId);
                }
                break;
            case SignalFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case ChallengeFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            default:
                handled = true;
                break;
            }
        }

        return handled;
    }

    private boolean handleWriteReply(
        long originId,
        long routedId,
        long streamId,
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean handled;

        if ((msgTypeId & 0x4000_0000) == 0)
        {
            supplyMetricRecorder.apply(ORIGIN, originId).onEvent(msgTypeId, buffer, index, length);
            supplyMetricRecorder.apply(ROUTED, routedId).onEvent(msgTypeId, buffer, index, length);
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                supplyLoadEntry.apply(routedId).replyOpened(1L);
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case DataFW.TYPE_ID:
                int bytesWritten = Math.max(buffer.getInt(index + DataFW.FIELD_OFFSET_LENGTH), 0);
                supplyLoadEntry.apply(routedId).replyBytesWritten(bytesWritten);
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case EndFW.TYPE_ID:
                supplyLoadEntry.apply(routedId).replyClosed(1L);
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                throttles[throttleIndex(streamId)].remove(instanceId(streamId));
                break;
            case AbortFW.TYPE_ID:
                supplyLoadEntry.apply(routedId).replyClosed(1L).replyErrored(1L);
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                throttles[throttleIndex(streamId)].remove(instanceId(streamId));
                break;
            case FlushFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            default:
                handled = true;
                break;
            }
        }
        else
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                streams[streamIndex(streamId)].remove(instanceId(streamId));
                correlations.remove(streamId);
                LongHashSet streamIdSet = streamSets.get(routedId);
                if (streamIdSet != null)
                {
                    streamIdSet.remove(streamId);
                }
                break;
            case SignalFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            case ChallengeFW.TYPE_ID:
                handled = streamsBuffer.test(msgTypeId, buffer, index, length);
                break;
            default:
                handled = true;
                break;
            }
        }

        return handled;
    }

    private void doSyntheticReset(
        long streamId,
        MessageConsumer sender)
    {
        final long syntheticId = 0L;

        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(syntheticId)
                .routedId(syntheticId)
                .streamId(streamId)
                .sequence(-1L)
                .acknowledge(-1L)
                .maximum(0)
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
