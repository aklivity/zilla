/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.bench;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayDeque;
import java.util.Queue;

import org.agrona.collections.LongArrayQueue;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

/**
 * Answers the Kafka streams that a clean-start session handshake with {@code publishQosMax} of 2 opens through
 * {@code mqtt-kafka}, the way the session scripts in {@code binding-mqtt-kafka.spec} specify: the retained topic
 * describe, a meta stream per topic, the group coordinator and offset delete api requests, the session offsets
 * fetch, init producer id, the offset commit produce stream and the sessions topic stream. A client with no
 * prior offsets is answered with a flush on the offsets fetch.
 * <p>
 * Produce streams are acknowledged by growing the stream window to the received position, as Kafka does, and
 * every answer is delayed by the configured latency; {@link #release()} delivers the delayed answers and must
 * be called from the thread that polls the engine. Stream state is pooled and frames are built into a reused
 * buffer, so the stub does not allocate in steady state.
 */
public final class KafkaStub implements BindingHandler
{
    private static final int FIND_COORDINATOR_API = 10;
    private static final int OFFSET_DELETE_API = 47;
    private static final int FLAGS_COMPLETE = 0x03;
    private static final int WRITE_BUFFER_CAPACITY = 64 * 1024;
    private static final int STREAM_WINDOW = 8192;
    private static final int SESSION_SIGNALS = 2;

    private static final byte[] FIND_COORDINATOR_RESPONSE =
    {
        0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
        0x00,
        0x00, 0x00, 0x00, 0x02,
        0x08, 'b', 'r', 'o', 'k', 'e', 'r', '2',
        0x00, 0x00, 0x23, (byte) 0x84,
        0x00
    };

    private static final byte[] OFFSET_DELETE_RESPONSE =
    {
        0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x02,
        0x00, 0x0d, 'm', 'q', 't', 't', '-', 'm', 'e', 's', 's', 'a', 'g', 'e', 's',
        0x00, 0x00, 0x00, 0x02,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x0d, 'm', 'q', 't', 't', '-', 'r', 'e', 't', 'a', 'i', 'n', 'e', 'd',
        0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private enum Kind
    {
        DESCRIBE,
        MESSAGES_META,
        RETAINED_META,
        FIND_COORDINATOR,
        OFFSET_DELETE,
        INIT_PRODUCER_ID,
        OFFSET_FETCH,
        OFFSET_COMMIT,
        SESSION,
        UNEXPECTED
    }

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final String16FW retainedTopicRO = new String16FW("mqtt-retained");
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[WRITE_BUFFER_CAPACITY]);
    private final Queue<StubStream> idleStreams = new ArrayDeque<>();
    private final Queue<Runnable> delayedSteps = new ArrayDeque<>();
    private final LongArrayQueue delayedDeadlines = new LongArrayQueue();
    private final long delayNanos;

    private final DirectBufferEx emptyEx = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx describeBeginEx;
    private final DirectBufferEx describeDataEx;
    private final DirectBufferEx messagesMetaDataEx;
    private final DirectBufferEx retainedMetaDataEx;
    private final DirectBufferEx findCoordinatorBeginEx;
    private final DirectBufferEx offsetDeleteBeginEx;
    private final DirectBufferEx initProducerIdBeginEx;
    private final DirectBufferEx findCoordinatorResponse = new UnsafeBufferEx(FIND_COORDINATOR_RESPONSE);
    private final DirectBufferEx offsetDeleteResponse = new UnsafeBufferEx(OFFSET_DELETE_RESPONSE);

    private int unexpected;

    public KafkaStub(
        int kafkaTypeId,
        long delayMillis)
    {
        this.delayNanos = MILLISECONDS.toNanos(delayMillis);

        this.describeBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .describe()
                .topic("mqtt-retained")
                .config("cleanup.policy")
                .build()
            .build());
        this.describeDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .describe()
                .config("cleanup.policy", "compact")
                .build()
            .build());
        this.messagesMetaDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .meta()
                .partition(0, -2)
                .partition(1, -2)
                .build()
            .build());
        this.retainedMetaDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .meta()
                .partition(0, -2)
                .build()
            .build());
        this.findCoordinatorBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .apiResponse()
                .length(FIND_COORDINATOR_RESPONSE.length)
                .version(3)
                .build()
            .build());
        this.offsetDeleteBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .apiResponse()
                .length(OFFSET_DELETE_RESPONSE.length)
                .version(0)
                .build()
            .build());
        this.initProducerIdBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .initProducerId()
                .producerId(1)
                .producerEpoch((short) 1)
                .build()
            .build());
    }

    public int unexpected()
    {
        return unexpected;
    }

    public int release()
    {
        int released = 0;

        if (!delayedSteps.isEmpty())
        {
            final long now = System.nanoTime();

            while (!delayedSteps.isEmpty() && delayedDeadlines.peekLong() - now <= 0L)
            {
                delayedDeadlines.pollLong();
                delayedSteps.poll().run();
                released++;
            }
        }

        return released;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        StubStream stream = idleStreams.poll();

        if (stream == null)
        {
            stream = new StubStream();
        }

        return stream.reset(sender);
    }

    private void schedule(
        Runnable step)
    {
        if (delayNanos == 0L)
        {
            step.run();
        }
        else
        {
            delayedSteps.add(step);
            delayedDeadlines.addLong(System.nanoTime() + delayNanos);
        }
    }

    private Kind kindOf(
        KafkaBeginExFW beginEx)
    {
        Kind kind = Kind.UNEXPECTED;

        switch (beginEx.kind())
        {
        case KafkaBeginExFW.KIND_DESCRIBE:
            kind = Kind.DESCRIBE;
            break;
        case KafkaBeginExFW.KIND_META:
            kind = beginEx.meta().topic().equals(retainedTopicRO) ? Kind.RETAINED_META : Kind.MESSAGES_META;
            break;
        case KafkaBeginExFW.KIND_API_REQUEST:
            final int api = beginEx.apiRequest().api();
            kind = api == FIND_COORDINATOR_API ? Kind.FIND_COORDINATOR : api == OFFSET_DELETE_API ? Kind.OFFSET_DELETE : kind;
            break;
        case KafkaBeginExFW.KIND_INIT_PRODUCER_ID:
            kind = Kind.INIT_PRODUCER_ID;
            break;
        case KafkaBeginExFW.KIND_MERGED:
            final KafkaCapabilities capabilities = beginEx.merged().capabilities().get();
            kind = switch (capabilities)
            {
            case FETCH_ONLY -> Kind.OFFSET_FETCH;
            case PRODUCE_ONLY -> Kind.OFFSET_COMMIT;
            case PRODUCE_AND_FETCH -> Kind.SESSION;
            };
            break;
        default:
            break;
        }

        return kind;
    }

    private final class StubStream
    {
        private final MessageConsumer consumer = this::onMessage;
        private final Runnable openStep = this::doOpen;
        private final Runnable replyStep = this::doReply;
        private final Runnable ackStep = this::doAck;

        private MessageConsumer sender;
        private Kind kind;
        private int state;
        private int pending;
        private boolean replyDue;
        private long originId;
        private long routedId;
        private long initialId;
        private long replyId;
        private long initialSeq;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private int requestLength;
        private int requestBytes;
        private int acks;

        private MessageConsumer reset(
            MessageConsumer sender)
        {
            this.sender = sender;
            this.kind = null;
            this.state = 0;
            this.pending = 0;
            this.replyDue = false;
            this.initialSeq = 0L;
            this.replySeq = 0L;
            this.replyAck = 0L;
            this.replyMax = 0;
            this.replyPad = 0;
            this.requestLength = 0;
            this.requestBytes = 0;
            this.acks = 0;
            return consumer;
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
                onBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                state = StreamState.closeInitial(state);
                doEnd();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                state = StreamState.openReply(state);
                doReply();
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                state = StreamState.closeReply(StreamState.closeInitial(state));
                doRecycle();
                break;
            default:
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            this.originId = begin.originId();
            this.routedId = begin.routedId();
            this.initialId = begin.streamId();
            this.replyId = initialId & ~1L;
            this.state = StreamState.openInitial(state);

            final KafkaBeginExFW beginEx = begin.extension().get(kafkaBeginExRO::tryWrap);
            this.kind = beginEx != null ? kindOf(beginEx) : Kind.UNEXPECTED;

            if (kind == Kind.UNEXPECTED)
            {
                unexpected++;
                doReset();
            }
            else
            {
                if (kind == Kind.FIND_COORDINATOR || kind == Kind.OFFSET_DELETE)
                {
                    this.requestLength = beginEx.apiRequest().length();
                }

                doInitialWindow();
                pending++;
                schedule(openStep);
            }
        }

        private void onData(
            DataFW data)
        {
            initialSeq += data.reserved();
            requestBytes += data.payload() != null ? data.payload().sizeof() : 0;

            switch (kind)
            {
            case FIND_COORDINATOR:
            case OFFSET_DELETE:
                if (requestBytes >= requestLength)
                {
                    replyDue = true;
                    doReply();
                }
                break;
            case OFFSET_COMMIT:
            case SESSION:
                pending++;
                schedule(ackStep);
                break;
            default:
                break;
            }
        }

        private void doOpen()
        {
            pending--;

            if (!StreamState.replyClosed(state))
            {
                doReplyBegin();

                switch (kind)
                {
                case DESCRIBE:
                case MESSAGES_META:
                case RETAINED_META:
                    replyDue = true;
                    break;
                case OFFSET_FETCH:
                    doReplyFlush();
                    break;
                default:
                    break;
                }

                doReply();
            }

            doEnd();
        }

        private void doReply()
        {
            if (replyDue && StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                final DirectBufferEx payload = switch (kind)
                {
                case FIND_COORDINATOR -> findCoordinatorResponse;
                case OFFSET_DELETE -> offsetDeleteResponse;
                default -> emptyEx;
                };

                final DirectBufferEx extension = switch (kind)
                {
                case DESCRIBE -> describeDataEx;
                case MESSAGES_META -> messagesMetaDataEx;
                case RETAINED_META -> retainedMetaDataEx;
                default -> emptyEx;
                };

                final int reserved = payload.capacity() + replyPad;

                if (replyAck + replyMax - replySeq >= reserved)
                {
                    doReplyData(payload, extension, reserved);
                    replyDue = false;
                }
            }
        }

        private void doAck()
        {
            pending--;

            if (!StreamState.initialClosed(state))
            {
                doInitialWindow();
            }

            if (kind == Kind.SESSION && ++acks == SESSION_SIGNALS && !StreamState.replyClosed(state))
            {
                doReplyFlush();
            }

            doEnd();
        }

        private void doEnd()
        {
            if (pending == 0 && StreamState.initialClosed(state) && !StreamState.replyClosed(state))
            {
                doReplyEnd();
            }

            doRecycle();
        }

        private void doRecycle()
        {
            if (pending == 0 && StreamState.closed(state))
            {
                idleStreams.offer(this);
            }
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
                .maximum(STREAM_WINDOW)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doReplyBegin()
        {
            state = StreamState.openingReply(state);

            final DirectBufferEx extension = switch (kind)
            {
            case DESCRIBE -> describeBeginEx;
            case FIND_COORDINATOR -> findCoordinatorBeginEx;
            case OFFSET_DELETE -> offsetDeleteBeginEx;
            case INIT_PRODUCER_ID -> initProducerIdBeginEx;
            default -> emptyEx;
            };

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .affinity(0L)
                .extension(extension, 0, extension.capacity())
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doReplyData(
            DirectBufferEx payload,
            DirectBufferEx extension,
            int reserved)
        {
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
                .payload(payload, 0, payload.capacity())
                .extension(extension, 0, extension.capacity())
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doReplyFlush()
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .budgetId(0L)
                .extension(emptyEx, 0, 0)
                .build();

            sender.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }

        private void doReplyEnd()
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
                .build();

            sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void doReset()
        {
            state = StreamState.closeReply(StreamState.closeInitial(state));

            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(0L)
                .acknowledge(0L)
                .state(state)
                .maximum(0)
                .build();

            sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            doRecycle();
        }
    }
}
