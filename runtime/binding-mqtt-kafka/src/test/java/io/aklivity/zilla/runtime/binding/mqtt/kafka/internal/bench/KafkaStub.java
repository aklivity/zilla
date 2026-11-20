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

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import org.agrona.collections.IntArrayQueue;
import org.agrona.collections.LongArrayQueue;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedConsumerFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaMergedProduceDataExFW;
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
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

/**
 * Answers the Kafka streams that a clean-start session handshake with {@code publishQosMax} of 2 opens through
 * {@code mqtt-kafka}, the way the session scripts in {@code binding-mqtt-kafka.spec} specify: the retained topic
 * describe, a meta stream per topic, the group coordinator and offset delete api requests, the session offsets
 * fetch, init producer id, the offset commit produce stream and the sessions topic stream. A client with no
 * prior offsets is answered with a flush on the offsets fetch. It also accepts the produce stream that a publish
 * opens on the messages topic, and answers the flush that a QoS 2 publish sends on it with the partition the
 * message is produced to.
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
    public static final int NO_DELIVERY = -2;

    private static final int PRODUCE_PARTITION = 0;
    private static final int QOS_LEVELS = 3;
    private static final int QOS_LEVELS_MASK = (1 << QOS_LEVELS) - 1;
    private static final int NO_QOS = -1;
    private static final long PROGRESS_OFFSET_MARKER = 0x1112131415161718L;
    private static final long PROGRESS_CORRELATION_MARKER = 0x2122232425262728L;
    private static final long NO_CORRELATION = -1L;
    private static final String QOS_HEADER = "zilla:qos";
    private static final byte[] WILL_KEY_POSTFIX = "#will".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EXPIRY_KEY_POSTFIX = "#expiry".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNAL_TYPE_NAME = "type".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WILL_SIGNAL_NAME = "will-signal".getBytes(StandardCharsets.UTF_8);
    private static final int PARTITION_FLUSH_CAPACITY = 1024;

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
        MESSAGES,
        RETAINED,
        RETAINED_REPLAY,
        MESSAGES_FETCH,
        MESSAGES_DELIVER,
        SHARE,
        SESSION,
        SESSION_STATE,
        WILL_SIGNALS,
        WILL_SIGNAL,
        WILL_SESSION,
        WILL_FETCH,
        UNEXPECTED
    }

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final String16FW retainedTopicRO = new String16FW("mqtt-retained");
    private final String16FW messagesTopicRO = new String16FW("mqtt-messages");
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
    private final List<StubStream> deliverables = new ArrayList<>();
    private final IntArrayQueue committed = new IntArrayQueue(-1);
    private final int kafkaTypeId;
    private final int deliveryQos;
    private final long delayNanos;
    private final byte[] progressTemplate;
    private final int progressOffsetPosition;
    private final int progressCorrelationPosition;

    private final DirectBufferEx emptyEx = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx describeBeginEx;
    private final DirectBufferEx describeDataEx;
    private final DirectBufferEx messagesMetaDataEx;
    private final DirectBufferEx retainedMetaDataEx;
    private final DirectBufferEx findCoordinatorBeginEx;
    private final DirectBufferEx offsetDeleteBeginEx;
    private final DirectBufferEx initProducerIdBeginEx;
    private final DirectBufferEx messagesFetchBeginEx;
    private final DirectBufferEx retainedFetchBeginEx;
    private final DirectBufferEx shareBeginEx;
    private final DirectBufferEx findCoordinatorResponse = new UnsafeBufferEx(FIND_COORDINATOR_RESPONSE);
    private final DirectBufferEx offsetDeleteResponse = new UnsafeBufferEx(OFFSET_DELETE_RESPONSE);

    private byte[] sessionStateTemplate;
    private int sessionStateKeyPosition;
    private int sessionStateKeyLength;
    private DirectBufferEx sessionStatePayload;
    private DirectBufferEx retainedReplayDataEx;
    private DirectBufferEx retainedReplayPayload;
    private int unexpected;
    private int excludedQos;
    private int replayCommits;
    private int willDeliveries;
    private String willTopic;
    private byte[] willTopicBytes;
    private StubStream willSignalStream;
    private final Map<String, byte[]> willMessages = new HashMap<>();

    public KafkaStub(
        int kafkaTypeId,
        long delayMillis)
    {
        this(kafkaTypeId, delayMillis, 1);
    }

    public KafkaStub(
        int kafkaTypeId,
        long delayMillis,
        int deliveryQos)
    {
        this.kafkaTypeId = kafkaTypeId;
        this.deliveryQos = deliveryQos;
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
        this.messagesFetchBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .merged()
                .capabilities("FETCH_ONLY")
                .topic("mqtt-messages")
                .partition(0, 0, 1, 1)
                .build()
            .build());
        this.retainedFetchBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .merged()
                .capabilities("FETCH_ONLY")
                .topic("mqtt-retained")
                .partition(0, 0, 1, 1)
                .build()
            .build());
        this.shareBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .share()
                .groupId("zilla.test-mqtt0.share.benchmark.ZGV2aWNlLyM.0")
                .topic("mqtt-messages")
                .host("broker1.example.com")
                .port(9092)
                .build()
            .build());
        this.progressTemplate = KafkaFunctions.flushEx()
            .typeId(kafkaTypeId)
            .merged()
                .consumer()
                    .progress(PRODUCE_PARTITION, PROGRESS_OFFSET_MARKER)
                    .correlationId(PROGRESS_CORRELATION_MARKER)
                    .build()
                .build();
        this.progressOffsetPosition = indexOfLong(progressTemplate, PROGRESS_OFFSET_MARKER);
        this.progressCorrelationPosition = indexOfLong(progressTemplate, PROGRESS_CORRELATION_MARKER);
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

    private static int indexOfLong(
        byte[] array,
        long value)
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(array);
        int index = -1;

        for (int offset = 0; index == -1 && offset + Long.BYTES <= array.length; offset++)
        {
            if (buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN) == value)
            {
                index = offset;
            }
        }

        if (index == -1)
        {
            throw new IllegalStateException("marker not found in consumer progress flush extension");
        }

        return index;
    }

    /**
     * Makes the fetch stream with a group id on the retained topic replay one retained message with the key
     * {@code topic} and {@code payload}, as the retained replay scripts specify. Without it that stream only
     * answers the begin and the caught up flush.
     */
    public void retainedReplay(
        String topic,
        DirectBufferEx payload)
    {
        this.retainedReplayDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .merged()
                .fetch()
                    .timestamp(1L)
                    .filters(1L)
                    .partition(0, 2L, 2L)
                    .key(topic)
                    .header("zilla:qos", "1")
                    .build()
                .build());
        this.retainedReplayPayload = payload;
    }

    /**
     * Makes the session stream of a client with a stored session state send {@code payload} as that state, in a
     * fetch data extension keyed by the client id, which is {@code keyLength} bytes long, as the non clean start
     * session scripts specify. Without it the session stream answers as for a clean start.
     */
    public void sessionState(
        int keyLength,
        DirectBufferEx payload)
    {
        final String keyMarker = "k".repeat(keyLength);

        this.sessionStateTemplate = KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .merged()
                .fetch()
                    .deferred(0)
                    .partition(-1, -1)
                    .key(keyMarker)
                    .build()
                .build();
        this.sessionStateKeyLength = keyLength;
        this.sessionStateKeyPosition = indexOfBytes(sessionStateTemplate, keyMarker.getBytes(StandardCharsets.UTF_8));
        this.sessionStatePayload = payload;
    }

    private static int indexOfBytes(
        byte[] array,
        byte[] pattern)
    {
        int index = -1;

        for (int offset = 0; index == -1 && offset + pattern.length <= array.length; offset++)
        {
            int length = 0;
            while (length < pattern.length && array[offset + length] == pattern[length])
            {
                length++;
            }

            if (length == pattern.length)
            {
                index = offset;
            }
        }

        if (index == -1)
        {
            throw new IllegalStateException("key marker not found in session state data extension");
        }

        return index;
    }

    /**
     * Makes the stub answer the streams a session with a will opens, as the session will scripts specify, and
     * count each will message delivered to {@code topic}.
     */
    public void willDelivery(
        String topic)
    {
        this.willTopic = topic;
        this.willTopicBytes = topic.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns whether a will message has been delivered to its topic, consuming it.
     */
    public boolean willDelivered()
    {
        final boolean available = willDeliveries > 0;

        if (available)
        {
            willDeliveries--;
        }

        return available;
    }

    /**
     * Returns whether the offset commit of a replayed retained message has arrived, consuming it.
     */
    public boolean replayCommitted()
    {
        final boolean available = replayCommits > 0;

        if (available)
        {
            replayCommits--;
        }

        return available;
    }

    /**
     * Prepares the delivery stream opened {@code index}th, counting the streams that fetch with a group id, to
     * send {@code payload} with {@code dataEx} as the fetch data extension. {@code dataEx} is a data extension
     * built with a partition offset of {@code offsetMarker}, which is replaced by the offset of each delivery.
     */
    public void prepare(
        int index,
        byte[] dataEx,
        long offsetMarker,
        DirectBufferEx payload)
    {
        deliverables.get(index).prepare(dataEx, offsetMarker, payload);
    }

    /**
     * Delivers one message on the {@code index}th delivery stream, after the configured latency.
     */
    public void deliver(
        int index)
    {
        deliverables.get(index).requestDelivery();
    }

    /**
     * Returns the index of a delivery stream whose offset commit has arrived, or -1.
     */
    public int committed()
    {
        return committed.pollInt();
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
        case KafkaBeginExFW.KIND_SHARE:
            kind = Kind.SHARE;
            break;
        case KafkaBeginExFW.KIND_MERGED:
            final KafkaCapabilities capabilities = beginEx.merged().capabilities().get();
            kind = switch (capabilities)
            {
            case FETCH_ONLY -> beginEx.merged().topic().equals(messagesTopicRO)
                ? deliveryKind(beginEx.merged())
                : retainedReplayKind(beginEx.merged());
            case PRODUCE_ONLY -> beginEx.merged().topic().equals(messagesTopicRO) ? Kind.MESSAGES :
                beginEx.merged().topic().equals(retainedTopicRO) ? Kind.RETAINED :
                willTopic != null && hasKeyFilter(beginEx.merged()) ? Kind.WILL_SESSION : Kind.OFFSET_COMMIT;
            case PRODUCE_AND_FETCH -> sessionStateTemplate != null && hasKeyFilter(beginEx.merged())
                ? Kind.SESSION_STATE
                : willTopic == null ? Kind.SESSION
                : hasWillKeyFilter(beginEx.merged()) ? Kind.WILL_SIGNALS
                : hasKeyFilter(beginEx.merged()) ? Kind.SESSION : Kind.WILL_SIGNAL;
            };
            break;
        default:
            break;
        }

        return kind;
    }

    private Kind deliveryKind(
        KafkaMergedBeginExFW merged)
    {
        return merged.groupId().length() > 0 && qosOf(merged) == deliveryQos ? Kind.MESSAGES_DELIVER : Kind.MESSAGES_FETCH;
    }

    private boolean hasKeyFilter(
        KafkaMergedBeginExFW merged)
    {
        return merged.filters().anyMatch(filter -> filter.conditions().anyMatch(c -> c.kind() == KafkaConditionFW.KIND_KEY));
    }

    private boolean hasWillKeyFilter(
        KafkaMergedBeginExFW merged)
    {
        return merged.filters().anyMatch(filter -> filter.conditions().anyMatch(c -> c.kind() == KafkaConditionFW.KIND_KEY &&
            endsWith(c.key(), WILL_KEY_POSTFIX)));
    }

    private static byte[] copyOf(
        OctetsFW octets)
    {
        final byte[] copy = new byte[octets.sizeof()];
        octets.buffer().getBytes(octets.offset(), copy);
        return copy;
    }

    private static byte[] copyOf(
        DirectBufferEx buffer,
        int length)
    {
        final byte[] copy = new byte[length];
        buffer.getBytes(0, copy);
        return copy;
    }

    private static boolean endsWith(
        KafkaKeyFW key,
        byte[] postfix)
    {
        boolean matches = key.length() >= postfix.length;

        for (int index = 0; matches && index < postfix.length; index++)
        {
            final int position = key.value().offset() + key.length() - postfix.length + index;
            matches = key.value().buffer().getByte(position) == postfix[index];
        }

        return matches;
    }

    private Kind retainedReplayKind(
        KafkaMergedBeginExFW merged)
    {
        return retainedReplayDataEx != null && merged.topic().equals(retainedTopicRO)
            ? merged.groupId().length() > 0 ? Kind.RETAINED_REPLAY : Kind.OFFSET_FETCH
            : willTopic != null && hasKeyFilter(merged) ? Kind.WILL_FETCH : Kind.OFFSET_FETCH;
    }

    private int qosOf(
        KafkaMergedBeginExFW merged)
    {
        excludedQos = 0;
        merged.filters().forEach(filter -> filter.conditions().forEach(this::excludeQos));

        final int included = ~excludedQos & QOS_LEVELS_MASK;

        return included != 0 ? Integer.numberOfTrailingZeros(included) : NO_QOS;
    }

    private void excludeQos(
        KafkaConditionFW condition)
    {
        if (condition.kind() == KafkaConditionFW.KIND_NOT && condition.not().condition().kind() == KafkaConditionFW.KIND_HEADER)
        {
            final KafkaHeaderFW header = condition.not().condition().header();

            if (header.name().buffer().getStringWithoutLengthUtf8(header.name().offset(), header.nameLen()).equals(QOS_HEADER))
            {
                excludedQos |= 1 << (header.value().buffer().getByte(header.value().offset()) - '0');
            }
        }
    }

    private final class StubStream
    {
        private final MessageConsumer consumer = this::onMessage;
        private final Runnable openStep = this::doOpen;
        private final Runnable replyStep = this::doReply;
        private final Runnable ackStep = this::doAck;
        private final Runnable deliverStep = this::doDeliverStep;
        private final Runnable partitionStep = this::doPartitionReply;
        private final Runnable progressStep = this::doProgressReply;
        private final Runnable sessionStateStep = this::doNoSessionState;
        private final Queue<DirectBufferEx> echoPayloads = new ArrayDeque<>();
        private final Queue<DirectBufferEx> echoExtensions = new ArrayDeque<>();
        private final Consumer<KafkaConditionFW> keyCapture = this::captureSessionKey;
        private final Consumer<KafkaConditionFW> willKeyCapture = this::captureWillKey;
        private final Consumer<KafkaFilterFW> willFilterCapture = filter -> filter.conditions().forEach(willKeyCapture);
        private final Consumer<KafkaFilterFW> filterCapture = filter -> filter.conditions().forEach(keyCapture);
        private final MutableDirectBufferEx partitionEx = new UnsafeBufferEx(new byte[PARTITION_FLUSH_CAPACITY]);

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
        private int deliverIndex;
        private int deliveries;
        private int partitionExLength;
        private MutableDirectBufferEx progressEx;
        private MutableDirectBufferEx sessionStateEx;
        private DirectBufferEx willFetchPayload;
        private DirectBufferEx willFetchEx;
        private byte[] willFetchKey;
        private long deliveryOffset;
        private byte[] deliveryBytes;
        private MutableDirectBufferEx deliveryEx;
        private int[] offsetPositions;
        private ByteOrder offsetOrder;
        private DirectBufferEx deliveryPayload;

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
            this.deliveries = 0;
            this.willFetchPayload = null;
            this.deliveryOffset = 2L;
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
                doDeliver();
                doReplay();
                doEchoes();
                break;
            case FlushFW.TYPE_ID:
                if (kind == Kind.MESSAGES_DELIVER || kind == Kind.RETAINED_REPLAY)
                {
                    onDeliveryFlush(flushRO.wrap(buffer, index, index + length));
                }
                else if (delivering())
                {
                    committed.addInt(deliverIndex);
                }
                else if (kind == Kind.MESSAGES)
                {
                    onProduceFlush(flushRO.wrap(buffer, index, index + length));
                }
                else if (kind == Kind.WILL_SESSION)
                {
                    pending++;
                    schedule(sessionStateStep);
                }
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

                if (delivering())
                {
                    deliverIndex = deliverables.size();
                    deliverables.add(this);
                }

                if (kind == Kind.WILL_SIGNAL)
                {
                    willSignalStream = this;
                }

                if (kind == Kind.WILL_FETCH)
                {
                    beginEx.merged().filters().forEach(willFilterCapture);
                }

                if (kind == Kind.SESSION_STATE)
                {
                    if (sessionStateEx == null)
                    {
                        sessionStateEx = new UnsafeBufferEx(sessionStateTemplate.clone());
                    }

                    beginEx.merged().filters().forEach(filterCapture);
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

            if (kind == Kind.WILL_SIGNAL)
            {
                forgetWillMessage(data);
            }

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
            case MESSAGES:
            case RETAINED:
            case SESSION:
            case SESSION_STATE:
            case WILL_SESSION:
                if (kind == Kind.WILL_SESSION)
                {
                    onWillSessionData(data);
                }

                if (kind == Kind.MESSAGES && willTopic != null)
                {
                    onMessagesData(data);
                }

                pending++;
                schedule(ackStep);
                break;
            default:
                break;
            }
        }

        private void onProduceFlush(
            FlushFW flush)
        {
            final KafkaFlushExFW flushEx = flush.extension().get(kafkaFlushExRO::tryWrap);

            if (flushEx != null && flushEx.kind() == KafkaFlushExFW.KIND_MERGED &&
                flushEx.merged().kind() == KafkaMergedFlushExFW.KIND_PRODUCE)
            {
                final KafkaKeyFW hashKey = flushEx.merged().produce().hashKey();

                partitionExLength = kafkaFlushExRW.wrap(partitionEx, 0, partitionEx.capacity())
                    .typeId(kafkaTypeId)
                    .merged(m -> m.produce(p -> p.hashKey(hashKey).partitionId(PRODUCE_PARTITION)))
                    .build()
                    .sizeof();

                pending++;
                schedule(partitionStep);
            }
        }

        private void onDeliveryFlush(
            FlushFW flush)
        {
            final OctetsFW extension = flush.extension();
            final KafkaFlushExFW flushEx = kafkaFlushExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaMergedConsumerFlushExFW consumerFlushEx = flushEx != null &&
                flushEx.kind() == KafkaFlushExFW.KIND_MERGED && flushEx.merged().kind() == KafkaMergedFlushExFW.KIND_CONSUMER
                ? flushEx.merged().consumer()
                : null;
            final long correlationId = consumerFlushEx != null ? consumerFlushEx.correlationId() : NO_CORRELATION;

            if (correlationId == NO_CORRELATION && kind == Kind.MESSAGES_DELIVER)
            {
                committed.addInt(deliverIndex);
            }
            else if (consumerFlushEx != null)
            {
                if (kind == Kind.RETAINED_REPLAY && correlationId == NO_CORRELATION)
                {
                    replayCommits++;
                }

                if (progressEx == null)
                {
                    progressEx = new UnsafeBufferEx(progressTemplate.clone());
                }

                progressEx.putLong(progressOffsetPosition, consumerFlushEx.progress().partitionOffset(), ByteOrder.LITTLE_ENDIAN);
                progressEx.putLong(progressCorrelationPosition, correlationId, ByteOrder.LITTLE_ENDIAN);

                pending++;
                schedule(progressStep);
            }
        }

        private void onMessagesData(
            DataFW data)
        {
            final KafkaDataExFW dataEx = data.extension().get(kafkaDataExRO::tryWrap);

            if (dataEx != null && dataEx.kind() == KafkaDataExFW.KIND_MERGED &&
                dataEx.merged().kind() == KafkaMergedDataExFW.KIND_PRODUCE &&
                dataEx.merged().produce().key().length() == willTopicBytes.length)
            {
                final KafkaKeyFW key = dataEx.merged().produce().key();

                if (endsWith(key, willTopicBytes))
                {
                    willDeliveries++;
                }
            }
        }

        private void doNoSessionState()
        {
            pending--;

            if (!StreamState.replyClosed(state))
            {
                doReplyFlush(emptyEx, 0);
            }

            doEnd();
        }

        private void forgetWillMessage(
            DataFW data)
        {
            final KafkaDataExFW dataEx = data.extension().get(kafkaDataExRO::tryWrap);

            if (dataEx != null && dataEx.kind() == KafkaDataExFW.KIND_MERGED &&
                dataEx.merged().kind() == KafkaMergedDataExFW.KIND_PRODUCE && data.payload() == null)
            {
                final KafkaKeyFW key = dataEx.merged().produce().key();

                if (!endsWith(key, WILL_KEY_POSTFIX) && !endsWith(key, EXPIRY_KEY_POSTFIX))
                {
                    final byte[] keyBytes = new byte[key.length()];
                    key.value().buffer().getBytes(key.value().offset(), keyBytes);
                    willMessages.remove(new String(keyBytes, StandardCharsets.UTF_8));
                }
            }
        }

        private void onWillSessionData(
            DataFW data)
        {
            final KafkaDataExFW dataEx = data.extension().get(kafkaDataExRO::tryWrap);

            if (dataEx != null && dataEx.kind() == KafkaDataExFW.KIND_MERGED &&
                dataEx.merged().kind() == KafkaMergedDataExFW.KIND_PRODUCE && willSignalStream != null)
            {
                final KafkaMergedProduceDataExFW produce = dataEx.merged().produce();

                if (!endsWith(produce.key(), WILL_KEY_POSTFIX) && !endsWith(produce.key(), EXPIRY_KEY_POSTFIX))
                {
                    final byte[] keyBytes = new byte[produce.key().length()];
                    produce.key().value().buffer().getBytes(produce.key().value().offset(), keyBytes);
                    final String willKey = new String(keyBytes, StandardCharsets.UTF_8);

                    if (data.payload() != null)
                    {
                        willMessages.put(willKey, copyOf(data.payload()));
                    }
                    else
                    {
                        willMessages.remove(willKey);
                    }
                }

                if (endsWith(produce.key(), WILL_KEY_POSTFIX))
                {
                    final byte[] echoBytes = new byte[PARTITION_FLUSH_CAPACITY];
                    final MutableDirectBufferEx echoEx = new UnsafeBufferEx(echoBytes);
                    final int echoLength = kafkaDataExRW.wrap(echoEx, 0, echoEx.capacity())
                        .typeId(kafkaTypeId)
                        .merged(m -> m.fetch(f -> f
                            .deferred(0)
                            .partition(p -> p.partitionId(-1).partitionOffset(-1))
                            .key(k -> k.length(produce.key().length())
                                .value(produce.key().value().buffer(), produce.key().value().offset(), produce.key().length()))
                            .headersItem(h -> h
                                .nameLen(SIGNAL_TYPE_NAME.length)
                                .name(new UnsafeBufferEx(SIGNAL_TYPE_NAME), 0, SIGNAL_TYPE_NAME.length)
                                .valueLen(WILL_SIGNAL_NAME.length)
                                .value(new UnsafeBufferEx(WILL_SIGNAL_NAME), 0, WILL_SIGNAL_NAME.length))))
                        .build()
                        .sizeof();

                    final DirectBufferEx echoPayload = data.payload() != null
                        ? new UnsafeBufferEx(copyOf(data.payload())) : null;

                    schedule(() -> willSignalStream.doEcho(echoPayload, new UnsafeBufferEx(copyOf(echoEx, echoLength))));
                }
            }
        }

        private void doEcho(
            DirectBufferEx payload,
            DirectBufferEx extension)
        {
            echoPayloads.add(payload != null ? payload : emptyEx);
            echoExtensions.add(extension);
            doEchoes();
        }

        private void doEchoes()
        {
            while (!echoPayloads.isEmpty() && StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                final DirectBufferEx payload = echoPayloads.peek();
                final boolean tombstone = payload == emptyEx;
                final int reserved = (tombstone ? 0 : payload.capacity()) + replyPad;

                if (replyAck + replyMax - replySeq < reserved)
                {
                    break;
                }

                doReplyData(tombstone ? null : payload, echoExtensions.peek(), reserved);
                echoPayloads.poll();
                echoExtensions.poll();
            }
        }

        private void doProgressReply()
        {
            pending--;

            if (!StreamState.replyClosed(state))
            {
                doReplyFlush(progressEx, progressEx.capacity());
            }

            doEnd();
        }

        private DirectBufferEx replayPayload()
        {
            return switch (kind)
            {
            case SESSION_STATE -> sessionStatePayload;
            case WILL_FETCH -> willFetchPayload();
            default -> retainedReplayPayload;
            };
        }

        private DirectBufferEx replayExtension()
        {
            return switch (kind)
            {
            case SESSION_STATE -> sessionStateEx;
            case WILL_FETCH -> willFetchEx;
            default -> retainedReplayDataEx;
            };
        }

        private DirectBufferEx willFetchPayload()
        {
            if (willFetchPayload == null)
            {
                final byte[] message = willMessages.get(new String(willFetchKey, StandardCharsets.UTF_8));

                if (message == null)
                {
                    throw new IllegalStateException("will message not found for fetch");
                }

                willFetchPayload = new UnsafeBufferEx(message);
                final byte[] extension = KafkaFunctions.dataEx()
                    .typeId(kafkaTypeId)
                    .merged()
                        .fetch()
                            .deferred(0)
                            .partition(-1, -1)
                            .key(new String(willFetchKey, StandardCharsets.UTF_8))
                            .build()
                        .build();
                willFetchEx = new UnsafeBufferEx(extension);
            }

            return willFetchPayload;
        }

        private void captureWillKey(
            KafkaConditionFW condition)
        {
            if (condition.kind() == KafkaConditionFW.KIND_KEY)
            {
                final KafkaKeyFW key = condition.key();
                willFetchKey = new byte[key.length()];
                key.value().buffer().getBytes(key.value().offset(), willFetchKey);
                willFetchPayload = null;
            }
        }

        private void captureSessionKey(
            KafkaConditionFW condition)
        {
            if (condition.kind() == KafkaConditionFW.KIND_KEY)
            {
                final KafkaKeyFW key = condition.key();

                if (key.length() != sessionStateKeyLength)
                {
                    throw new IllegalStateException(String.format(
                        "session key length %d, expected %d", key.length(), sessionStateKeyLength));
                }

                sessionStateEx.putBytes(sessionStateKeyPosition, key.value().buffer(), key.value().offset(), key.length());
            }
        }

        private void doPartitionReply()
        {
            pending--;

            if (!StreamState.replyClosed(state))
            {
                doReplyFlush(partitionEx, partitionExLength);
            }

            doEnd();
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
                case WILL_SIGNALS:
                    doReplyFlush(emptyEx, 0);
                    break;
                case RETAINED_REPLAY:
                case SESSION_STATE:
                case WILL_FETCH:
                    deliveries = 1;
                    break;
                default:
                    break;
                }

                doReply();
                doReplay();
            }

            doEnd();
        }

        private void doReplay()
        {
            if ((kind == Kind.RETAINED_REPLAY || kind == Kind.SESSION_STATE || kind == Kind.WILL_FETCH) && deliveries > 0 &&
                StreamState.replyOpening(state) && !StreamState.replyClosed(state))
            {
                final DirectBufferEx payload = replayPayload();
                final DirectBufferEx extension = replayExtension();
                final int reserved = payload.capacity() + replyPad;

                if (replyAck + replyMax - replySeq >= reserved)
                {
                    doReplyData(payload, extension, reserved);

                    if (kind != Kind.WILL_FETCH)
                    {
                        doReplyFlush(emptyEx, 0);
                    }

                    deliveries--;
                }
            }
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

        private void prepare(
            byte[] dataEx,
            long offsetMarker,
            DirectBufferEx payload)
        {
            this.deliveryBytes = dataEx.clone();
            this.deliveryEx = new UnsafeBufferEx(deliveryBytes);
            this.deliveryPayload = payload;

            final UnsafeBufferEx template = new UnsafeBufferEx(dataEx);
            final List<Integer> positions = new ArrayList<>();
            ByteOrder order = ByteOrder.LITTLE_ENDIAN;
            for (ByteOrder candidate : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN})
            {
                for (int offset = 0; offset + Long.BYTES <= dataEx.length; offset++)
                {
                    if (template.getLong(offset, candidate) == offsetMarker)
                    {
                        positions.add(offset);
                        order = candidate;
                    }
                }

                if (!positions.isEmpty())
                {
                    break;
                }
            }

            if (positions.isEmpty())
            {
                throw new IllegalStateException("offset marker not found in delivery data extension");
            }

            this.offsetOrder = order;
            this.offsetPositions = positions.stream().mapToInt(Integer::intValue).toArray();
        }

        private boolean delivering()
        {
            return kind == Kind.MESSAGES_DELIVER || kind == Kind.SHARE;
        }

        private void requestDelivery()
        {
            pending++;
            schedule(deliverStep);
        }

        private void doDeliverStep()
        {
            pending--;
            deliveries++;
            doDeliver();
            doEnd();
        }

        private void doDeliver()
        {
            if (delivering() && deliveryEx != null)
            {
                final int reserved = deliveryPayload.capacity() + replyPad;

                while (deliveries > 0 && StreamState.replyOpening(state) && !StreamState.replyClosed(state) &&
                    replyAck + replyMax - replySeq >= reserved)
                {
                    for (int position : offsetPositions)
                    {
                        deliveryEx.putLong(position, deliveryOffset, offsetOrder);
                    }

                    doReplyData(deliveryPayload, deliveryEx, reserved);
                    deliveryOffset++;
                    deliveries--;
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
                doReplyFlush(emptyEx, 0);
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
            if (sender != null && pending == 0 && StreamState.closed(state))
            {
                sender = null;
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
            case MESSAGES_FETCH, MESSAGES_DELIVER -> messagesFetchBeginEx;
            case RETAINED_REPLAY -> retainedFetchBeginEx;
            case SHARE -> shareBeginEx;
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
            final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(FLAGS_COMPLETE)
                .budgetId(0L)
                .reserved(reserved);
            final DataFW.Builder withPayload = payload != null
                ? builder.payload(payload, 0, payload.capacity())
                : builder.payload((OctetsFW) null);
            final DataFW data = withPayload
                .extension(extension, 0, extension.capacity())
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        private void doReplyFlush(
            DirectBufferEx extension,
            int extensionLength)
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
                .extension(extension, 0, extensionLength)
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
