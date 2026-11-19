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
package io.aklivity.zilla.runtime.binding.mqtt.internal.bench;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.internal.types.stream.MqttSubscribeBeginExFW;
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
import io.aklivity.zilla.runtime.engine.test.EngineBench.FrameCounter;

/**
 * Accepts the subscribe streams of an mqtt server, replies to each and keeps it open, and delivers one message at
 * a time on the QoS 1 stream of a client subscribed to {@code device/<client>}, within the reply credit the
 * server has granted. Streams are pooled and the delivery extension is built once per stream, so a delivery does
 * not allocate.
 */
final class Deliverer implements BindingHandler
{
    private static final DirectBufferEx EMPTY_EXTENSION = new UnsafeBufferEx(new byte[0]);
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;
    private static final int EXTENSION_CAPACITY = 256;
    private static final int TOPIC_CAPACITY = 256;
    private static final int INITIAL_WINDOW = 8192;
    private static final int FLAGS_COMPLETE = 0x03;
    private static final int PACKET_ID = 1;
    private static final int QOS_AT_LEAST_ONCE = 1;
    private static final int NONE = -1;

    private final BeginFW beginRO = new BeginFW();
    private final WindowFW windowRO = new WindowFW();
    private final MqttBeginExFW beginExRO = new MqttBeginExFW();
    private final MqttTopicFilterFW filterRO = new MqttTopicFilterFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MqttDataExFW.Builder dataExRW = new MqttDataExFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<DeliveryStream> idleStreams = new ArrayDeque<>();
    private final List<DeliveryStream> deliverable = new ArrayList<>();
    private final FrameCounter counter = new FrameCounter();
    private final int mqttTypeId;

    private DirectBufferEx payload = EMPTY_EXTENSION;

    Deliverer(
        int mqttTypeId)
    {
        this.mqttTypeId = mqttTypeId;
    }

    FrameCounter counter()
    {
        return counter;
    }

    void payload(
        int length)
    {
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'x');
        this.payload = new UnsafeBufferEx(bytes);
    }

    boolean credited(
        int client)
    {
        final DeliveryStream stream = client < deliverable.size() ? deliverable.get(client) : null;

        return stream != null && stream.replyCredit() >= payload.capacity() + stream.replyPad;
    }

    void deliver(
        int client)
    {
        deliverable.get(client).deliver();
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        DeliveryStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new DeliveryStream();
        }

        return stream.reset(sender);
    }

    private final class DeliveryStream
    {
        private final MessageConsumer consumer = this::onMessage;
        private final MutableDirectBufferEx topic = new UnsafeBufferEx(new byte[TOPIC_CAPACITY]);
        private final MutableDirectBufferEx dataEx = new UnsafeBufferEx(new byte[EXTENSION_CAPACITY]);

        private MessageConsumer sender;
        private int state;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private int qos;
        private int client;
        private int filterId;
        private int topicLength;
        private int dataExLength;

        private MessageConsumer reset(
            MessageConsumer sender)
        {
            this.sender = sender;
            this.state = 0;
            this.replySeq = 0L;
            this.replyAck = 0L;
            this.replyMax = 0;
            this.replyPad = 0;
            this.qos = NONE;
            this.client = NONE;
            this.dataExLength = NONE;
            return consumer;
        }

        private int replyCredit()
        {
            return (int) (replyAck + replyMax - replySeq);
        }

        private void deliver()
        {
            if (dataExLength == NONE)
            {
                final MqttDataExFW extension = dataExRW.wrap(dataEx, 0, dataEx.capacity())
                    .typeId(mqttTypeId)
                    .subscribe(s -> s.topic(topic, 0, topicLength)
                        .packetId(PACKET_ID)
                        .qos(QOS_AT_LEAST_ONCE)
                        .subscriptionIdsItem(i -> i.set(filterId))
                        .format(f -> f.set(MqttPayloadFormat.NONE)))
                    .build();
                dataExLength = extension.sizeof();
            }

            final int length = payload.capacity();
            final int reserved = length + replyPad;

            if (replyCredit() < reserved)
            {
                throw new IllegalStateException("delivery exceeds reply credit: " + replyCredit());
            }

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(FLAGS_COMPLETE)
                .budgetId(0L)
                .reserved(reserved)
                .payload(payload, 0, length)
                .extension(dataEx, 0, dataExLength)
                .build();

            replySeq += reserved;

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            counter.accept(msgTypeId, buffer, index, length);

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                state = StreamState.openReply(state);
                break;
            case EndFW.TYPE_ID:
                onEnd();
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                closeStream();
                break;
            default:
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            originId = begin.originId();
            routedId = begin.routedId();
            initialId = begin.streamId();
            replyId = initialId & ~1L;
            state = StreamState.openInitial(state);

            final OctetsFW extension = begin.extension();
            final MqttBeginExFW beginEx = beginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());

            if (beginEx != null && beginEx.kind() == MqttBeginExFW.KIND_SUBSCRIBE)
            {
                final MqttSubscribeBeginExFW subscribe = beginEx.subscribe();
                final DirectBufferEx items = subscribe.filters().items();
                final MqttTopicFilterFW filter = filterRO.wrap(items, 0, items.capacity());

                qos = subscribe.qos();
                filterId = filter.subscriptionId();
                topicLength = filter.pattern().length();
                topic.putBytes(0, filter.pattern().buffer(), filter.pattern().offset() + Short.BYTES, topicLength);
                client = clientOf(topic, topicLength);
            }

            doInitialWindow();
            doReplyBegin();

            if (qos == QOS_AT_LEAST_ONCE && client != NONE)
            {
                while (deliverable.size() <= client)
                {
                    deliverable.add(null);
                }
                deliverable.set(client, this);
            }
        }

        private int clientOf(
            DirectBufferEx buffer,
            int length)
        {
            int value = 0;
            int scale = 1;
            int index = length - 1;

            while (index >= 0 && buffer.getByte(index) >= '0' && buffer.getByte(index) <= '9')
            {
                value += (buffer.getByte(index) - '0') * scale;
                scale *= 10;
                index--;
            }

            return index == length - 1 ? NONE : value;
        }

        private void doInitialWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(0L)
                .acknowledge(0L)
                .state(state)
                .maximum(INITIAL_WINDOW)
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
                .extension(EMPTY_EXTENSION, 0, 0)
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void onEnd()
        {
            state = StreamState.closeReply(state);

            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .extension(EMPTY_EXTENSION, 0, 0)
                .build();

            sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());

            final boolean closed = StreamState.closed(state);
            state = StreamState.closeInitial(state);
            release(closed);
        }

        private void closeStream()
        {
            final boolean closed = StreamState.closed(state);

            state = StreamState.closeReply(StreamState.closeInitial(state));

            release(closed);
        }

        private void release(
            boolean closed)
        {
            if (!closed && StreamState.closed(state))
            {
                if (client != NONE && client < deliverable.size() && deliverable.get(client) == this)
                {
                    deliverable.set(client, null);
                }
                idleStreams.offer(this);
            }
        }
    }
}
