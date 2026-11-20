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
package io.aklivity.zilla.runtime.binding.kafka.internal.bench;

import static java.nio.ByteOrder.BIG_ENDIAN;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.LongArrayQueue;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;

/**
 * Stands in for a Kafka broker connection: accepts the network stream opened by the client binding, reads
 * length-prefixed Kafka requests and answers each one, in order, once {@code delayNanos} has elapsed. Responses
 * are only produced by {@link #drain()}, which the benchmark thread calls, so the delay is observable
 * and a request is never answered from inside the frame that carried it.
 * <p>
 * Only {@code InitProducerId v4} is answered. Requests may be pipelined: every request found in a frame
 * is queued, and {@link #maxPipelined()} reports the deepest queue seen on any connection.
 */
final class KafkaBrokerStub implements BindingHandler
{
    private static final int API_KEY_INIT_PRODUCER_ID = 22;
    private static final int RESPONSE_LENGTH = 20;
    private static final int WINDOW = 64 * 1024;
    private static final int FLAGS_INIT_FIN = 0x03;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
    private final MutableDirectBufferEx responseBuffer = new UnsafeBufferEx(new byte[4 + RESPONSE_LENGTH]);
    private final List<BrokerStream> streams = new ArrayList<>();
    private final long delayNanos;

    private long requests;
    private int maxPipelined;

    KafkaBrokerStub(
        long delayNanos)
    {
        this.delayNanos = delayNanos;
    }

    long requests()
    {
        return requests;
    }

    int maxPipelined()
    {
        return maxPipelined;
    }

    int drain()
    {
        final long now = System.nanoTime();

        int work = 0;
        for (int index = 0; index < streams.size(); index++)
        {
            work += streams.get(index).drain(now);
        }

        return work;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BrokerStream stream = new BrokerStream(sender);
        streams.add(stream);
        return stream::onMessage;
    }

    private final class BrokerStream
    {
        private final MessageConsumer sender;
        private final LongArrayQueue dueNanos = new LongArrayQueue();
        private final LongArrayQueue correlationIds = new LongArrayQueue();

        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long initialSeq;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private BrokerStream(
            MessageConsumer sender)
        {
            this.sender = sender;
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
                originId = begin.originId();
                routedId = begin.routedId();
                initialId = begin.streamId();
                replyId = initialId & ~1L;
                state = StreamState.openInitial(state);
                doInitialWindow();
                doReplyBegin();
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                final OctetsFW payload = data.payload();
                onRequests(payload.buffer(), payload.offset(), payload.limit());
                doInitialWindow();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                state = StreamState.closeReply(StreamState.closeInitial(state));
                break;
            default:
                break;
            }
        }

        private void onRequests(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final long due = System.nanoTime() + delayNanos;

            int progress = offset;
            while (progress + 12 <= limit)
            {
                final int size = buffer.getInt(progress, BIG_ENDIAN);
                final int apiKey = buffer.getShort(progress + 4, BIG_ENDIAN);
                final int correlationId = buffer.getInt(progress + 8, BIG_ENDIAN);

                assert apiKey == API_KEY_INIT_PRODUCER_ID : "unexpected api key " + apiKey;

                dueNanos.addLong(due);
                correlationIds.addLong(correlationId);
                requests++;
                progress += 4 + size;
            }

            maxPipelined = Math.max(maxPipelined, dueNanos.size());
        }

        private int drain(
            long now)
        {
            int work = 0;

            while (!dueNanos.isEmpty() &&
                   dueNanos.peekLong() <= now &&
                   replyAck + replyMax - replySeq >= responseBuffer.capacity() + replyPad)
            {
                dueNanos.removeLong();
                doReplyData((int) correlationIds.removeLong());
                work++;
            }

            return work;
        }

        private void doInitialWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialSeq)
                .state(state)
                .maximum(WINDOW)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doReplyBegin()
        {
            state = StreamState.openingReply(state);

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .affinity(0L)
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyData(
            int correlationId)
        {
            responseBuffer.putInt(0, RESPONSE_LENGTH, BIG_ENDIAN);
            responseBuffer.putInt(4, correlationId, BIG_ENDIAN);
            responseBuffer.putInt(8, 0, BIG_ENDIAN);
            responseBuffer.putShort(12, (short) 0, BIG_ENDIAN);
            responseBuffer.putLong(14, 1L, BIG_ENDIAN);
            responseBuffer.putShort(22, (short) 2, BIG_ENDIAN);

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(FLAGS_INIT_FIN)
                .budgetId(0L)
                .reserved(responseBuffer.capacity() + replyPad)
                .payload(responseBuffer, 0, responseBuffer.capacity())
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }
    }
}
