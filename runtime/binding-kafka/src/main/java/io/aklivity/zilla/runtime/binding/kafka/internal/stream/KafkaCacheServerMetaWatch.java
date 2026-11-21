/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

final class KafkaCacheServerMetaWatch
{
    private static final int REPLY_MAX = 8192;

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();

    private final MutableDirectBufferEx writeBuffer;
    private final BindingHandler streamFactory;
    private final int kafkaTypeId;
    private final KafkaCacheRoute cacheRoute;
    private final int topicKey;
    private final long originId;
    private final long routedId;
    private final long authorization;
    private final String topic;
    private final long initialId;
    private final long replyId;

    private MessageConsumer receiver;
    private int state;
    private long replySeq;
    private long replyAck;

    static void open(
        EngineContext context,
        KafkaCacheRoute cacheRoute,
        long originId,
        long routedId,
        long authorization,
        String topic,
        long affinityId,
        long traceId)
    {
        final int topicKey = cacheRoute.topicKey(topic);

        if (!cacheRoute.serverMetaWatchesByTopic.containsKey(topicKey))
        {
            final KafkaCacheServerMetaWatch watch =
                new KafkaCacheServerMetaWatch(context, cacheRoute, topicKey, originId, routedId, authorization, topic,
                    affinityId);

            cacheRoute.serverMetaWatchesByTopic.put(topicKey, watch);
            watch.doInitialBegin(traceId);
        }
    }

    private KafkaCacheServerMetaWatch(
        EngineContext context,
        KafkaCacheRoute cacheRoute,
        int topicKey,
        long originId,
        long routedId,
        long authorization,
        String topic,
        long affinityId)
    {
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.cacheRoute = cacheRoute;
        this.topicKey = topicKey;
        this.originId = originId;
        this.routedId = routedId;
        this.authorization = authorization;
        this.topic = topic;
        this.initialId = context.supplyInitialId(routedId, affinityId);
        this.replyId = context.supplyReplyId(initialId);
    }

    private void doInitialBegin(
        long traceId)
    {
        state = StreamState.openingInitial(state);

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(0)
                .acknowledge(0)
                .state(state)
                .maximum(0)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .meta(m -> m.topic(topic))
                        .build()
                        .sizeof()))
                .build();

        receiver = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), this::onMessage);
        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void onMessage(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            final BeginFW begin = beginRO.wrap(buffer, index, index + length);
            onReplyBegin(begin);
            break;
        case DataFW.TYPE_ID:
            final DataFW data = dataRO.wrap(buffer, index, index + length);
            onReplyData(data);
            break;
        case EndFW.TYPE_ID:
            final EndFW end = endRO.wrap(buffer, index, index + length);
            onReplyEnd(end);
            break;
        case AbortFW.TYPE_ID:
            final AbortFW abort = abortRO.wrap(buffer, index, index + length);
            onReplyAbort(abort);
            break;
        case ResetFW.TYPE_ID:
            final ResetFW reset = resetRO.wrap(buffer, index, index + length);
            onInitialReset(reset);
            break;
        default:
            break;
        }
    }

    private void onReplyBegin(
        BeginFW begin)
    {
        state = StreamState.openingReply(state);

        doReplyWindow(begin.traceId());
    }

    private void onReplyData(
        DataFW data)
    {
        replySeq = data.sequence() + data.reserved();

        doReplyWindow(data.traceId());
    }

    private void onReplyEnd(
        EndFW end)
    {
        state = StreamState.closeReply(state);

        doClose();
    }

    private void onReplyAbort(
        AbortFW abort)
    {
        state = StreamState.closeReply(state);

        doClose();
    }

    private void onInitialReset(
        ResetFW reset)
    {
        state = StreamState.closeInitial(state);

        doClose();
    }

    private void doReplyWindow(
        long traceId)
    {
        replyAck = replySeq;
        state = StreamState.openReply(state);

        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(REPLY_MAX)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(0L)
                .padding(0)
                .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doClose()
    {
        cacheRoute.serverMetaWatchesByTopic.remove(topicKey);
    }
}
