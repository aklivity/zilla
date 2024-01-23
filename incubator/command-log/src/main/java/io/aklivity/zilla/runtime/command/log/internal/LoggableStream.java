/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.log.internal;

import static java.lang.String.format;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.HEADER_LENGTH;

import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.command.log.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.log.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.command.log.internal.spy.RingBufferSpy;
import io.aklivity.zilla.runtime.command.log.internal.types.AmqpPropertiesFW;
import io.aklivity.zilla.runtime.command.log.internal.types.Array32FW;
import io.aklivity.zilla.runtime.command.log.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaConfigFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaIsolation;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaNotFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaPartitionFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaSkipFW;
import io.aklivity.zilla.runtime.command.log.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.runtime.command.log.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.command.log.internal.types.MqttTopicFilterFW;
import io.aklivity.zilla.runtime.command.log.internal.types.MqttUserPropertyFW;
import io.aklivity.zilla.runtime.command.log.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyAddressInet4FW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyAddressInet6FW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyAddressUnixFW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.command.log.internal.types.ProxySecureInfoFW;
import io.aklivity.zilla.runtime.command.log.internal.types.String16FW;
import io.aklivity.zilla.runtime.command.log.internal.types.StringFW;
import io.aklivity.zilla.runtime.command.log.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.AmqpBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.AmqpDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.HttpFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaBootstrapBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaConsumerAssignmentFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaConsumerBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaConsumerDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaDescribeBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaFetchDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaGroupBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaGroupFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedConsumerFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedFetchDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedFetchFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMergedProduceDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMetaBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaMetaDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaProduceBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaProduceDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.KafkaTopicPartitionFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttPublishFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttSessionBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttSubscribeBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttSubscribeDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.MqttSubscribeFlushExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.runtime.command.log.internal.types.stream.WindowFW;
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

    private final ExtensionFW extensionRO = new ExtensionFW();

    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpFlushExFW httpFlushExRO = new HttpFlushExFW();
    private final HttpEndExFW httpEndExRO = new HttpEndExFW();
    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final GrpcResetExFW grpcResetExRO = new GrpcResetExFW();
    private final GrpcAbortExFW grpcAbortExRO = new GrpcAbortExFW();
    private final SseDataExFW sseDataExRO = new SseDataExFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();
    private final MqttFlushExFW mqttFlushExRO = new MqttFlushExFW();
    private final AmqpBeginExFW amqpBeginExRO = new AmqpBeginExFW();
    private final AmqpDataExFW amqpDataExRO = new AmqpDataExFW();

    private final int index;
    private final LabelManager labels;
    private final String streamFormat;
    private final String throttleFormat;
    private final String verboseFormat;
    private final StreamsLayout layout;
    private final RingBufferSpy streamsBuffer;
    private final Logger out;
    private final boolean withPayload;
    private final LongPredicate nextTimestamp;

    private final Int2ObjectHashMap<MessageConsumer> frameHandlers;
    private final Int2ObjectHashMap<Consumer<BeginFW>> beginHandlers;
    private final Int2ObjectHashMap<Consumer<DataFW>> dataHandlers;
    private final Int2ObjectHashMap<Consumer<EndFW>> endHandlers;
    private final Int2ObjectHashMap<Consumer<FlushFW>> flushHandlers;
    private final Int2ObjectHashMap<Consumer<ResetFW>> resetHandlers;

    LoggableStream(
        int index,
        LabelManager labels,
        StreamsLayout layout,
        Logger logger,
        Predicate<String> hasFrameType,
        Predicate<String> hasExtensionType,
        boolean withPayload,
        LongPredicate nextTimestamp)
    {
        this.index = index;
        this.labels = labels;
        this.streamFormat = "[%02d/%08x] [%d] [0x%016x] [%s.%s]\t[0x%016x] [0x%016x] [0x%016x] [%d/%d] %s\n";
        this.throttleFormat = "[%02d/%08x] [%d] [0x%016x] [%s.%s]\t[0x%016x] [0x%016x] [0x%016x] [%d/%d] %s\n";
        this.verboseFormat = "[%02d/%08x] [%d] %s\n";

        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.out = logger;
        this.withPayload = withPayload;
        this.nextTimestamp = nextTimestamp;

        final Int2ObjectHashMap<MessageConsumer> frameHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<BeginFW>> beginHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<DataFW>> dataHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<EndFW>> endHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<FlushFW>> flushHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<ResetFW>> resetHandlers = new Int2ObjectHashMap<>();
        final Int2ObjectHashMap<Consumer<AbortFW>> abortHandlers = new Int2ObjectHashMap<>();

        if (hasFrameType.test("BEGIN"))
        {
            frameHandlers.put(BeginFW.TYPE_ID, (t, b, i, l) -> onBegin(beginRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("DATA"))
        {
            frameHandlers.put(DataFW.TYPE_ID, (t, b, i, l) -> onData(dataRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("END"))
        {
            frameHandlers.put(EndFW.TYPE_ID, (t, b, i, l) -> onEnd(endRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("ABORT"))
        {
            frameHandlers.put(AbortFW.TYPE_ID, (t, b, i, l) -> onAbort(abortRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("WINDOW"))
        {
            frameHandlers.put(WindowFW.TYPE_ID, (t, b, i, l) -> onWindow(windowRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("RESET"))
        {
            frameHandlers.put(ResetFW.TYPE_ID, (t, b, i, l) -> onReset(resetRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("CHALLENGE"))
        {
            frameHandlers.put(ChallengeFW.TYPE_ID, (t, b, i, l) -> onChallenge(challengeRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("SIGNAL"))
        {
            frameHandlers.put(SignalFW.TYPE_ID, (t, b, i, l) -> onSignal(signalRO.wrap(b, i, i + l)));
        }
        if (hasFrameType.test("FLUSH"))
        {
            frameHandlers.put(FlushFW.TYPE_ID, (t, b, i, l) -> onFlush(flushRO.wrap(b, i, i + l)));
        }

        if (hasExtensionType.test("proxy") || hasExtensionType.test("tcp") || hasExtensionType.test("tls"))
        {
            beginHandlers.put(labels.lookupLabelId("proxy"), this::onProxyBeginEx);
        }

        if (hasExtensionType.test("http"))
        {
            beginHandlers.put(labels.lookupLabelId("http"), this::onHttpBeginEx);
            flushHandlers.put(labels.lookupLabelId("http"), this::onHttpFlushEx);
            endHandlers.put(labels.lookupLabelId("http"), this::onHttpEndEx);
        }

        if (hasExtensionType.test("grpc"))
        {
            beginHandlers.put(labels.lookupLabelId("grpc"), this::onGrpcBeginEx);
            dataHandlers.put(labels.lookupLabelId("grpc"), this::onGrpcDataEx);
            abortHandlers.put(labels.lookupLabelId("grpc"), this::onGrpcAbortEx);
            resetHandlers.put(labels.lookupLabelId("grpc"), this::onGrpcResetEx);
        }

        if (hasExtensionType.test("sse"))
        {
            dataHandlers.put(labels.lookupLabelId("sse"), this::onSseDataEx);
        }

        if (hasExtensionType.test("kafka"))
        {
            beginHandlers.put(labels.lookupLabelId("kafka"), this::onKafkaBeginEx);
            dataHandlers.put(labels.lookupLabelId("kafka"), this::onKafkaDataEx);
            flushHandlers.put(labels.lookupLabelId("kafka"), this::onKafkaFlushEx);
            resetHandlers.put(labels.lookupLabelId("kafka"), this::onKafkaResetEx);
        }

        if (hasExtensionType.test("mqtt"))
        {
            beginHandlers.put(labels.lookupLabelId("mqtt"), this::onMqttBeginEx);
            dataHandlers.put(labels.lookupLabelId("mqtt"), this::onMqttDataEx);
            flushHandlers.put(labels.lookupLabelId("mqtt"), this::onMqttFlushEx);
        }

        if (hasExtensionType.test("amqp"))
        {
            beginHandlers.put(labels.lookupLabelId("amqp"), this::onAmqpBeginEx);
            dataHandlers.put(labels.lookupLabelId("amqp"), this::onAmqpDataEx);
        }

        this.frameHandlers = frameHandlers;
        this.beginHandlers = beginHandlers;
        this.dataHandlers = dataHandlers;
        this.endHandlers = endHandlers;
        this.flushHandlers = flushHandlers;
        this.resetHandlers = resetHandlers;
    }

    int process()
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

    private void onBegin(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long streamId = begin.streamId();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();
        final int maximum = begin.maximum();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(streamFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("BEGIN [0x%016x] [0x%016x]", authorization, affinity));


        final ExtensionFW extension = begin.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            final Consumer<BeginFW> beginHandler = beginHandlers.get(extension.typeId());
            if (beginHandler != null)
            {
                beginHandler.accept(begin);
            }
        }
    }

    private void onData(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final long originId = data.originId();
        final long routedId = data.routedId();
        final long streamId = data.streamId();
        final long sequence = data.sequence();
        final long acknowledge = data.acknowledge();
        final long traceId = data.traceId();
        final long budgetId = data.budgetId();
        final int maximum = data.maximum();
        final int length = data.length();
        final int reserved = data.reserved();
        final long authorization = data.authorization();
        final byte flags = (byte) (data.flags() & 0xFF);

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(streamFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
            sequence - acknowledge + reserved, maximum, format("DATA [0x%016x] [%d] [%d] [%x] [0x%016x]",
                    budgetId, length, reserved, flags, authorization));

        if (withPayload)
        {
            final OctetsFW payload = data.payload();
            if (payload != null)
            {
                byte[] bytes = new byte[data.length()];
                payload.buffer().getBytes(0, bytes);
                String hexData = BitUtil.toHex(bytes).replaceAll("(..)(?!$)", "$1:");
                out.printf(verboseFormat, index, offset, timestamp, hexData);
            }
        }

        final ExtensionFW extension = data.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            final Consumer<DataFW> dataHandler = dataHandlers.get(extension.typeId());
            if (dataHandler != null)
            {
                dataHandler.accept(data);
            }
        }
    }

    private void onEnd(
        final EndFW end)
    {
        final int offset = end.offset() - HEADER_LENGTH;
        final long timestamp = end.timestamp();
        final long originId = end.originId();
        final long routedId = end.routedId();
        final long streamId = end.streamId();
        final long sequence = end.sequence();
        final long acknowledge = end.acknowledge();
        final int maximum = end.maximum();
        final long traceId = end.traceId();
        final long authorization = end.authorization();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(streamFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("END [0x%016x]", authorization));

        final ExtensionFW extension = end.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            final Consumer<EndFW> endHandler = endHandlers.get(extension.typeId());
            if (endHandler != null)
            {
                endHandler.accept(end);
            }
        }
    }

    private void onAbort(
        final AbortFW abort)
    {
        final int offset = abort.offset() - HEADER_LENGTH;
        final long timestamp = abort.timestamp();
        final long originId = abort.originId();
        final long routedId = abort.routedId();
        final long streamId = abort.streamId();
        final long sequence = abort.sequence();
        final long acknowledge = abort.acknowledge();
        final int maximum = abort.maximum();
        final long traceId = abort.traceId();
        final long authorization = abort.authorization();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(streamFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("ABORT [0x%016x]", authorization));
    }

    private void onReset(
        final ResetFW reset)
    {
        final int offset = reset.offset() - HEADER_LENGTH;
        final long timestamp = reset.timestamp();
        final long originId = reset.originId();
        final long routedId = reset.routedId();
        final long streamId = reset.streamId();
        final long sequence = reset.sequence();
        final long acknowledge = reset.acknowledge();
        final int maximum = reset.maximum();
        final long traceId = reset.traceId();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(throttleFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, "RESET");

        final ExtensionFW extension = reset.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            final Consumer<ResetFW> resetHandler = resetHandlers.get(extension.typeId());
            if (resetHandler != null)
            {
                resetHandler.accept(reset);
            }
        }
    }

    private void onWindow(
        final WindowFW window)
    {
        final int offset = window.offset() - HEADER_LENGTH;
        final long timestamp = window.timestamp();
        final long originId = window.originId();
        final long routedId = window.routedId();
        final long streamId = window.streamId();
        final long sequence = window.sequence();
        final long acknowledge = window.acknowledge();
        final int maximum = window.maximum();
        final long traceId = window.traceId();
        final long budgetId = window.budgetId();
        final int minimum = window.minimum();
        final int padding = window.padding();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(throttleFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("WINDOW [0x%016x] [%d] [%d]", budgetId, minimum, padding));
    }

    private void onSignal(
        final SignalFW signal)
    {
        final int offset = signal.offset() - HEADER_LENGTH;
        final long timestamp = signal.timestamp();
        final long originId = signal.originId();
        final long routedId = signal.routedId();
        final long streamId = signal.streamId();
        final long sequence = signal.sequence();
        final long acknowledge = signal.acknowledge();
        final int maximum = signal.maximum();
        final long traceId = signal.traceId();
        final long authorization = signal.authorization();
        final long signalId = signal.signalId();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(throttleFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("SIGNAL [%d] [0x%016x]", signalId, authorization));
    }

    private void onChallenge(
        final ChallengeFW challenge)
    {
        final int offset = challenge.offset() - HEADER_LENGTH;
        final long timestamp = challenge.timestamp();
        final long originId = challenge.originId();
        final long routedId = challenge.routedId();
        final long streamId = challenge.streamId();
        final long sequence = challenge.sequence();
        final long acknowledge = challenge.acknowledge();
        final int maximum = challenge.maximum();
        final long traceId = challenge.traceId();
        final long authorization = challenge.authorization();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(throttleFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("CHALLENGE [0x%016x]", authorization));
    }

    private void onFlush(
        final FlushFW flush)
    {
        final int offset = flush.offset() - HEADER_LENGTH;
        final long timestamp = flush.timestamp();
        final long originId = flush.originId();
        final long routedId = flush.routedId();
        final long streamId = flush.streamId();
        final long sequence = flush.sequence();
        final long acknowledge = flush.acknowledge();
        final int maximum = flush.maximum();
        final long traceId = flush.traceId();
        final long authorization = flush.authorization();
        final long budgetId = flush.budgetId();

        final int namespaceId = (int)(routedId >> 32) & 0xffff_ffff;
        final int bindingId = (int)(routedId >> 0) & 0xffff_ffff;
        final String namespace = labels.lookupLabel(namespaceId);
        final String binding = labels.lookupLabel(bindingId);

        out.printf(streamFormat, index, offset, timestamp, traceId, namespace, binding, originId, routedId, streamId,
                sequence - acknowledge, maximum, format("FLUSH [0x%016x] [0x%016x]", budgetId, authorization));

        final ExtensionFW extension = flush.extension().get(extensionRO::tryWrap);
        if (extension != null)
        {
            final Consumer<FlushFW> flushHandler = flushHandlers.get(extension.typeId());
            if (flushHandler != null)
            {
                flushHandler.accept(flush);
            }
        }
    }

    private void onProxyBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final ProxyBeginExFW proxyBeginEx = proxyBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        final ProxyAddressFW address = proxyBeginEx.address();
        final Array32FW<ProxyInfoFW> infos = proxyBeginEx.infos();

        onProxyBeginExAddress(offset, timestamp, address);
        infos.forEach(info -> onProxyBeginExInfo(offset, timestamp, info));
    }

    private void onProxyBeginExAddress(
        final int offset,
        final long timestamp,
        final ProxyAddressFW address)
    {
        switch (address.kind())
        {
        case INET:
            onProxyBeginExAddressInet(offset, timestamp, address.inet());
            break;
        case INET4:
            onProxyBeginExAddressInet4(offset, timestamp, address.inet4());
            break;
        case INET6:
            onProxyBeginExAddressInet6(offset, timestamp, address.inet6());
            break;
        case UNIX:
            onProxyBeginExAddressUnix(offset, timestamp, address.unix());
            break;
        default:
            break;
        }
    }

    private void onProxyBeginExAddressInet(
        final int offset,
        final long timestamp,
        final ProxyAddressInetFW address)
    {
        final String source = address.source().asString();
        final int sourcePort = address.sourcePort();
        final String destination = address.destination().asString();
        final int destinationPort = address.destinationPort();

        out.printf(verboseFormat, index, offset, timestamp,
                   format("%s:%d\t%s:%d", source, sourcePort, destination, destinationPort));
    }

    private void onProxyBeginExAddressInet4(
        final int offset,
        final long timestamp,
        final ProxyAddressInet4FW address)
    {
        final DirectBuffer source = address.source().value();
        final int sourcePort = address.sourcePort();
        final DirectBuffer destination = address.destination().value();
        final int destinationPort = address.destinationPort();

        out.printf(verboseFormat, index, offset, timestamp,
                   format("%d.%d.%d.%d:%d\t%d.%d.%d.%d:%d",
                          source.getByte(0) & 0xff, source.getByte(1) & 0xff,
                          source.getByte(2) & 0xff, source.getByte(3) & 0xff,
                          sourcePort,
                          destination.getByte(0) & 0xff, destination.getByte(1) & 0xff,
                          destination.getByte(2) & 0xff, destination.getByte(3) & 0xff,
                          destinationPort));
    }

    private void onProxyBeginExAddressInet6(
        final int offset,
        final long timestamp,
        final ProxyAddressInet6FW address)
    {
        final DirectBuffer source = address.source().value();
        final int sourcePort = address.sourcePort();
        final DirectBuffer destination = address.destination().value();
        final int destinationPort = address.destinationPort();

        out.printf(verboseFormat, index, offset, timestamp,
               format("[%x:%x:%x:%x:%x:%x:%x:%x]:%d\t[%x:%x:%x:%x:%x:%x:%x:%x]:%d",
                      source.getShort(0, BIG_ENDIAN) & 0xffff, source.getShort(2, BIG_ENDIAN) & 0xffff,
                      source.getShort(4, BIG_ENDIAN) & 0xffff, source.getShort(6, BIG_ENDIAN) & 0xffff,
                      source.getShort(8, BIG_ENDIAN) & 0xffff, source.getShort(10, BIG_ENDIAN) & 0xffff,
                      source.getShort(12, BIG_ENDIAN) & 0xffff, source.getShort(14, BIG_ENDIAN) & 0xffff,
                      sourcePort,
                      destination.getShort(0, BIG_ENDIAN) & 0xffff, destination.getShort(2, BIG_ENDIAN) & 0xffff,
                      destination.getShort(4, BIG_ENDIAN) & 0xffff, destination.getShort(6, BIG_ENDIAN) & 0xffff,
                      destination.getShort(8, BIG_ENDIAN) & 0xffff, destination.getShort(10, BIG_ENDIAN) & 0xffff,
                      destination.getShort(12, BIG_ENDIAN) & 0xffff, destination.getShort(14, BIG_ENDIAN) & 0xffff,
                      destinationPort));
    }

    private void onProxyBeginExAddressUnix(
        final int offset,
        final long timestamp,
        final ProxyAddressUnixFW address)
    {
        final String source = asString(address.source());
        final String destination = asString(address.destination());

        out.printf(verboseFormat, index, offset, timestamp,
                   format("%s\t%s", source, destination));
    }

    private void onProxyBeginExInfo(
        final int offset,
        final long timestamp,
        final ProxyInfoFW info)
    {
        switch (info.kind())
        {
        case ALPN:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("alpn: %s", info.alpn().asString()));
            break;
        case AUTHORITY:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("authority: %s", info.authority().asString()));
            break;
        case IDENTITY:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("identity: %s", asString(info.identity().value())));
            break;
        case NAMESPACE:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("namespace: %s", info.namespace().asString()));
            break;
        case SECURE:
            onProxyBeginExSecureInfo(offset, timestamp, info.secure());
            break;
        }
    }

    private void onProxyBeginExSecureInfo(
        final int offset,
        final long timestamp,
        final ProxySecureInfoFW info)
    {
        switch (info.kind())
        {
        case CIPHER:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("cipher: %s", info.cipher().asString()));
            break;
        case KEY:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("key: %s", info.key().asString()));
            break;
        case NAME:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("name: %s", info.name().asString()));
            break;
        case VERSION:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("version: %s", info.version().asString()));
            break;
        case SIGNATURE:
            out.printf(verboseFormat, index, offset, timestamp,
                       format("signature: %s", info.signature().asString()));
            break;
        }
    }

    private void onHttpBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final HttpBeginExFW httpBeginEx = httpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        httpBeginEx.headers()
                   .forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
                                           format("%s: %s", h.name().asString(), h.value().asString())));
    }

    private void onHttpFlushEx(
        final FlushFW flush)
    {
        final int offset = flush.offset() - HEADER_LENGTH;
        final long timestamp = flush.timestamp();
        final OctetsFW extension = flush.extension();

        final HttpFlushExFW httpFlushEx = httpFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        httpFlushEx.promise()
                   .forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
                       format("%s: %s", h.name().asString(), h.value().asString())));
    }

    private void onHttpEndEx(
        final EndFW end)
    {
        final int offset = end.offset() - HEADER_LENGTH;
        final long timestamp = end.timestamp();
        final OctetsFW extension = end.extension();

        final HttpEndExFW httpEndEx = httpEndExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        httpEndEx.trailers()
                 .forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
                                         format("%s: %s", h.name().asString(), h.value().asString())));
    }

    private void onGrpcBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final GrpcBeginExFW grpcBeginEx = grpcBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        out.printf(verboseFormat, index, offset, timestamp, format("scheme: %s", grpcBeginEx.scheme().asString()));
        out.printf(verboseFormat, index, offset, timestamp, format("authority: %s", grpcBeginEx.authority().asString()));
        out.printf(verboseFormat, index, offset, timestamp, format("service: %s", grpcBeginEx.service().asString()));
        out.printf(verboseFormat, index, offset, timestamp, format("method: %s", grpcBeginEx.method().asString()));

        grpcBeginEx.metadata().forEach(m ->
        {
            OctetsFW metadataName = m.name();
            OctetsFW metadataValue = m.value();
            final String formattedMetadataName = metadataName.buffer().getStringWithoutLengthUtf8(
                metadataName.offset(), metadataName.sizeof());
            final String formattedMetadataValue = metadataValue.buffer().getStringWithoutLengthUtf8(
                metadataValue.offset(), metadataValue.sizeof());
            out.printf(verboseFormat, index, offset, timestamp,
                format("%s: %s", formattedMetadataName, formattedMetadataValue));
        });
    }

    private void onGrpcDataEx(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final OctetsFW extension = data.extension();

        final GrpcDataExFW grpcDataEx = grpcDataExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        out.printf(verboseFormat, index, offset, timestamp, format("deferred: %d", grpcDataEx.deferred()));
    }

    private void onGrpcAbortEx(
        final AbortFW abort)
    {
        final int offset = abort.offset() - HEADER_LENGTH;
        final long timestamp = abort.timestamp();
        final OctetsFW extension = abort.extension();

        final GrpcAbortExFW grpcAbortEx = grpcAbortExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        out.printf(verboseFormat, index, offset, timestamp, format("status: %s", grpcAbortEx.status().asString()));
    }

    private void onGrpcResetEx(
        final ResetFW reset)
    {
        final int offset = reset.offset() - HEADER_LENGTH;
        final long timestamp = reset.timestamp();
        final OctetsFW extension = reset.extension();

        final GrpcResetExFW grpcResetEx = grpcResetExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        out.printf(verboseFormat, index, offset, timestamp, format("status: %s", grpcResetEx.status().asString()));
    }

    private void onSseDataEx(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final OctetsFW extension = data.extension();

        final SseDataExFW sseDataEx = sseDataExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        out.printf(verboseFormat, index, offset, timestamp,
                format("type: %s", sseDataEx.type().asString()));
        out.printf(verboseFormat, index, offset, timestamp,
                format("id: %s", sseDataEx.id().asString()));
    }

    private void onKafkaBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final KafkaBeginExFW kafkaBeginEx = kafkaBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        switch (kafkaBeginEx.kind())
        {
        case KafkaBeginExFW.KIND_BOOTSTRAP:
            onKafkaBootstrapBeginEx(offset, timestamp, kafkaBeginEx.bootstrap());
            break;
        case KafkaBeginExFW.KIND_MERGED:
            onKafkaMergedBeginEx(offset, timestamp, kafkaBeginEx.merged());
            break;
        case KafkaBeginExFW.KIND_DESCRIBE:
            onKafkaDescribeBeginEx(offset, timestamp, kafkaBeginEx.describe());
            break;
        case KafkaBeginExFW.KIND_GROUP:
            onKafkaGroupBeginEx(offset, timestamp, kafkaBeginEx.group());
            break;
        case KafkaBeginExFW.KIND_CONSUMER:
            onKafkaConsumerBeginEx(offset, timestamp, kafkaBeginEx.consumer());
            break;
        case KafkaBeginExFW.KIND_FETCH:
            onKafkaFetchBeginEx(offset, timestamp, kafkaBeginEx.fetch());
            break;
        case KafkaBeginExFW.KIND_META:
            onKafkaMetaBeginEx(offset, timestamp, kafkaBeginEx.meta());
            break;
        case KafkaBeginExFW.KIND_PRODUCE:
            onKafkaProduceBeginEx(offset, timestamp, kafkaBeginEx.produce());
            break;
        }
    }

    private void onKafkaBootstrapBeginEx(
        int offset,
        long timestamp,
        KafkaBootstrapBeginExFW bootstrap)
    {
        final String16FW topic = bootstrap.topic();

        out.printf(verboseFormat, index, offset, timestamp, format("[bootstrap] %s", topic.asString()));
    }

    private void onKafkaMergedBeginEx(
        int offset,
        long timestamp,
        KafkaMergedBeginExFW merged)
    {
        final String16FW topic = merged.topic();
        final ArrayFW<KafkaOffsetFW> partitions = merged.partitions();
        final KafkaCapabilities capabilities = merged.capabilities().get();
        final Array32FW<KafkaFilterFW> filters = merged.filters();
        final KafkaIsolation isolation = merged.isolation().get();

        out.printf(verboseFormat, index, offset, timestamp,
                format("[merged] %s %s %s", topic.asString(), capabilities, isolation));
        partitions.forEach(p -> out.printf(verboseFormat, index, offset, timestamp,
                                         format("%d: %d %d %d",
                                             p.partitionId(),
                                             p.partitionOffset(),
                                             p.stableOffset(),
                                             p.latestOffset())));
        filters.forEach(f -> f.conditions().forEach(c -> out.printf(verboseFormat, index, offset, timestamp, asString(c))));
    }

    private void onKafkaDescribeBeginEx(
        int offset,
        long timestamp,
        KafkaDescribeBeginExFW describe)
    {
        final String16FW topic = describe.topic();
        final ArrayFW<String16FW> configs = describe.configs();

        out.printf(verboseFormat, index, offset, timestamp, format("[describe] %s", topic.asString()));
        configs.forEach(c -> out.printf(verboseFormat, index, offset, timestamp, c.asString()));
    }

    private void onKafkaGroupBeginEx(
        int offset,
        long timestamp,
        KafkaGroupBeginExFW group)
    {
        String16FW groupId = group.groupId();
        String16FW protocol = group.protocol();
        int timeout = group.timeout();

        out.printf(verboseFormat, index, offset, timestamp, format("[group] %s %s %d",
            groupId.asString(), protocol.asString(), timeout));
    }

    private void onKafkaConsumerBeginEx(
        int offset,
        long timestamp,
        KafkaConsumerBeginExFW consumer)
    {
        String16FW groupId = consumer.groupId();
        String16FW consumerId = consumer.consumerId();
        String16FW topic = consumer.topic();
        int timeout = consumer.timeout();
        Array32FW<KafkaTopicPartitionFW> partitions = consumer.partitionIds();

        out.printf(verboseFormat, index, offset, timestamp, format("[consumer] %s %s %s %d",
            groupId.asString(), consumerId.asString(), topic.asString(), timeout));
        partitions.forEach(p -> out.printf(verboseFormat, index, offset, timestamp, format("%d", p.partitionId())));
    }

    private void onKafkaFetchBeginEx(
        int offset,
        long timestamp,
        KafkaFetchBeginExFW fetch)
    {
        final String16FW topic = fetch.topic();
        final KafkaOffsetFW partition = fetch.partition();
        final ArrayFW<KafkaFilterFW> filters = fetch.filters();
        final KafkaIsolation isolation = fetch.isolation().get();

        out.printf(verboseFormat, index, offset, timestamp, format("[fetch] %s %s", topic.asString(), isolation));
        out.printf(verboseFormat, index, offset, timestamp,
                   format("%d: %d %d %d",
                           partition.partitionId(),
                           partition.partitionOffset(),
                           partition.stableOffset(),
                           partition.latestOffset()));
        filters.forEach(f -> f.conditions().forEach(c -> out.printf(verboseFormat, index, offset, timestamp, asString(c))));
    }

    private String asString(
        KafkaConditionFW condition)
    {
        String formatted = "unknown";
        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            final KafkaKeyFW key = condition.key();
            formatted = String.format("key[%d]", key.length());
            break;
        case KafkaConditionFW.KIND_HEADER:
            final KafkaHeaderFW header = condition.header();
            final OctetsFW headerName = header.name();
            final String formattedHeaderName = headerName.buffer().getStringWithoutLengthUtf8(
                headerName.offset(), headerName.sizeof());
            formatted = String.format("header[%s=[%d]]", formattedHeaderName, header.valueLen());
            break;
        case KafkaConditionFW.KIND_NOT:
            final KafkaNotFW not = condition.not();
            formatted = String.format("not[%s]", asString(not.condition()));
            break;
        case KafkaConditionFW.KIND_HEADERS:
            final KafkaHeadersFW headers = condition.headers();
            final OctetsFW headersName = headers.name();
            final Array32FW<KafkaValueMatchFW> values = headers.values();
            final String formattedHeadersName = headersName.buffer().getStringWithoutLengthUtf8(
                headersName.offset(), headersName.sizeof());
            final StringBuilder formattedValues = new StringBuilder();
            values.forEach(v ->
            {
                switch (v.kind())
                {
                case KafkaValueMatchFW.KIND_VALUE:
                    final int length = v.value().length();
                    formattedValues.append(length);
                    break;
                case KafkaValueMatchFW.KIND_SKIP:
                    final KafkaSkipFW skip = v.skip();
                    switch (skip.get())
                    {
                    case SKIP:
                        formattedValues.append('s');
                        break;
                    case SKIP_MANY:
                        formattedValues.append('S');
                        break;
                    }
                    break;
                }
                formattedValues.append(' ');
            });
            formatted = String.format("headers[%s=[%s]]", formattedHeadersName, formattedValues.toString());
            break;
        }
        return formatted;
    }

    private void onKafkaMetaBeginEx(
        int offset,
        long timestamp,
        KafkaMetaBeginExFW meta)
    {
        final String16FW topic = meta.topic();

        out.printf(verboseFormat, index, offset, timestamp, format("[meta] %s", topic.asString()));
    }

    private void onKafkaProduceBeginEx(
        int offset,
        long timestamp,
        KafkaProduceBeginExFW produce)
    {
        final String16FW topic = produce.topic();
        final long partitionId = produce.partition().partitionId();
        final long partitionOffset = produce.partition().partitionOffset();
        final long latestOffset = produce.partition().latestOffset();
        final StringFW transaction = produce.transaction();

        out.printf(verboseFormat, index, offset, timestamp,
                   format("[produce] %s %d %d %d %s", topic.asString(), partitionId, partitionOffset,
                   latestOffset, transaction.asString()));
    }

    private void onKafkaDataEx(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final OctetsFW extension = data.extension();

        final KafkaDataExFW kafkaDataEx = kafkaDataExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        switch (kafkaDataEx.kind())
        {
        case KafkaDataExFW.KIND_DESCRIBE:
            onKafkaDescribeDataEx(offset, timestamp, kafkaDataEx.describe());
            break;
        case KafkaDataExFW.KIND_FETCH:
            onKafkaFetchDataEx(offset, timestamp, kafkaDataEx.fetch());
            break;
        case KafkaDataExFW.KIND_CONSUMER:
            onKafkaConsumerDataEx(offset, timestamp, kafkaDataEx.consumer());
            break;
        case KafkaDataExFW.KIND_MERGED:
            onKafkaMergedDataEx(offset, timestamp, kafkaDataEx.merged());
            break;
        case KafkaDataExFW.KIND_META:
            onKafkaMetaDataEx(offset, timestamp, kafkaDataEx.meta());
            break;
        case KafkaDataExFW.KIND_PRODUCE:
            onKafkaProduceDataEx(offset, timestamp, kafkaDataEx.produce());
            break;
        }
    }

    private void onKafkaDescribeDataEx(
        int offset,
        long timestamp,
        KafkaDescribeDataExFW describe)
    {
        final ArrayFW<KafkaConfigFW> configs = describe.configs();

        out.printf(verboseFormat, index, offset, timestamp, "[describe]");
        configs.forEach(c -> out.printf(verboseFormat, index, offset, timestamp,
                                        format("%s: %s", c.name().asString(), c.value().asString())));
    }

    private void onKafkaFetchDataEx(
        int offset,
        long timestamp,
        KafkaFetchDataExFW fetch)
    {
        final KafkaKeyFW key = fetch.key();
        final ArrayFW<KafkaHeaderFW> headers = fetch.headers();
        final KafkaOffsetFW partition = fetch.partition();

        out.printf(verboseFormat, index, offset, timestamp,
                   format("[fetch] (%d) %d %s %d %d %d %d",
                           fetch.deferred(), fetch.timestamp(), asString(key.value()),
                           partition.partitionId(),
                           partition.partitionOffset(),
                           partition.stableOffset(),
                           partition.latestOffset()));
        headers.forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
                                        format("%s: %s", asString(h.name()), asString(h.value()))));
    }

    private void onKafkaConsumerDataEx(
        int offset,
        long timestamp,
        KafkaConsumerDataExFW consumer)
    {
        Array32FW<KafkaTopicPartitionFW> partitions = consumer.partitions();
        Array32FW<KafkaConsumerAssignmentFW> assignments = consumer.assignments();

        out.printf(verboseFormat, index, offset, timestamp, "[consumer]");
        partitions.forEach(p -> out.printf(verboseFormat, index, offset, timestamp,
            format("%d", p.partitionId())));
        assignments.forEach(a ->
        {
            out.printf(verboseFormat, index, offset, timestamp, a.consumerId().asString());
            a.partitions().forEach(p ->
                out.printf(verboseFormat, index, offset, timestamp, format("%d", p.partitionId())));
        });
    }

    private void onKafkaMergedDataEx(
        int offset,
        long timestamp,
        KafkaMergedDataExFW merged)
    {
        switch (merged.kind())
        {
        case KafkaMergedDataExFW.KIND_FETCH:
            onKafkaMergedFetchDataEx(offset, timestamp, merged.fetch());
            break;
        case KafkaMergedDataExFW.KIND_PRODUCE:
            onKafkaMergedProduceDataEx(offset, timestamp, merged.produce());
            break;
        }
    }

    private void onKafkaMergedFetchDataEx(
        int offset,
        long timestamp,
        KafkaMergedFetchDataExFW fetch)
    {
        final KafkaKeyFW key = fetch.key();
        final ArrayFW<KafkaHeaderFW> headers = fetch.headers();
        final KafkaOffsetFW partition = fetch.partition();
        final ArrayFW<KafkaOffsetFW> progress = fetch.progress();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[merged] [fetch] (%d) %d %s %d %d %d",
                fetch.deferred(), fetch.timestamp(), asString(key.value()),
                partition.partitionId(), partition.partitionOffset(), partition.latestOffset()));
        headers.forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s: %s", asString(h.name()), asString(h.value()))));
        progress.forEach(p -> out.printf(verboseFormat, index, offset, timestamp,
            format("%d: %d %d", p.partitionId(), p.partitionOffset(), p.latestOffset())));
    }

    private void onKafkaMergedProduceDataEx(
        int offset,
        long timestamp,
        KafkaMergedProduceDataExFW produce)
    {
        final KafkaKeyFW key = produce.key();
        final ArrayFW<KafkaHeaderFW> headers = produce.headers();
        final KafkaOffsetFW partition = produce.partition();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[merged] [produce] (%d) %d %s %d %d %d",
                produce.deferred(), produce.timestamp(), asString(key.value()),
                partition.partitionId(), partition.partitionOffset(), partition.latestOffset()));
        headers.forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s: %s", asString(h.name()), asString(h.value()))));
    }

    private void onKafkaMetaDataEx(
        int offset,
        long timestamp,
        KafkaMetaDataExFW meta)
    {
        final ArrayFW<KafkaPartitionFW> partitions = meta.partitions();

        out.printf(verboseFormat, index, offset, timestamp, "[meta]");
        partitions.forEach(p -> out.printf(verboseFormat, index, offset, timestamp,
                                           format("%d: %d", p.partitionId(), p.leaderId())));
    }

    private void onKafkaProduceDataEx(
        int offset,
        long timestamp,
        KafkaProduceDataExFW produce)
    {
        final KafkaKeyFW key = produce.key();
        final ArrayFW<KafkaHeaderFW> headers = produce.headers();

        out.printf(verboseFormat, index, offset, timestamp,
                   format("[produce] (%d) %s", produce.deferred(), asString(key.value())));
        headers.forEach(h -> out.printf(verboseFormat, index, offset, timestamp,
                                        format("%s: %s", asString(h.name()), asString(h.value()))));
    }

    private void onKafkaFlushEx(
        final FlushFW flush)
    {
        final int offset = flush.offset() - HEADER_LENGTH;
        final long timestamp = flush.timestamp();
        final OctetsFW extension = flush.extension();

        final KafkaFlushExFW kafkaFlushEx = kafkaFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        switch (kafkaFlushEx.kind())
        {
        case KafkaFlushExFW.KIND_MERGED:
            onKafkaMergedFlushEx(offset, timestamp, kafkaFlushEx.merged());
            break;
        case KafkaFlushExFW.KIND_GROUP:
            onKafkaGroupFlushEx(offset, timestamp, kafkaFlushEx.group());
            break;
        case KafkaFlushExFW.KIND_FETCH:
            onKafkaFetchFlushEx(offset, timestamp, kafkaFlushEx.fetch());
            break;
        }
    }

    private void onKafkaMergedFlushEx(
        int offset,
        long timestamp,
        KafkaMergedFlushExFW merged)
    {
        switch (merged.kind())
        {
        case KafkaFlushExFW.KIND_FETCH:
            onKafkaMergedFetchFlushEx(offset, timestamp, merged.fetch());
            break;
        case KafkaFlushExFW.KIND_CONSUMER:
            onKafkaMergedConsumerFlushEx(offset, timestamp, merged.consumer());
            break;
        }
    }

    private void onKafkaMergedFetchFlushEx(
        int offset,
        long timestamp,
        KafkaMergedFetchFlushExFW fetch)
    {
        final ArrayFW<KafkaOffsetFW> progress = fetch.progress();
        final Array32FW<KafkaFilterFW> filters = fetch.filters();

        out.printf(verboseFormat, index, offset, timestamp, "[merged] [fetch]");
        progress.forEach(p -> out.printf(verboseFormat, index, offset, timestamp,
            format("%d: %d %d %d",
                p.partitionId(),
                p.partitionOffset(),
                p.stableOffset(),
                p.latestOffset())));
        filters.forEach(f -> f.conditions().forEach(c -> out.printf(verboseFormat, index, offset, timestamp, asString(c))));
    }

    private void onKafkaMergedConsumerFlushEx(
        int offset,
        long timestamp,
        KafkaMergedConsumerFlushExFW consumer)
    {
        final KafkaOffsetFW progress = consumer.progress();
        final long correlationId = consumer.correlationId();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[merged] [consumer]  %d %d %d ",
                progress.partitionId(), progress.partitionOffset(), correlationId));
    }

    private void onKafkaGroupFlushEx(
        int offset,
        long timestamp,
        KafkaGroupFlushExFW group)
    {
        String16FW leader = group.leaderId();
        String16FW member = group.memberId();

        out.printf(verboseFormat, index, offset, timestamp, format("[group] %s %s (%d)", leader.asString(),
            member.asString(), group.members().fieldCount()));
    }

    private void onKafkaFetchFlushEx(
        int offset,
        long timestamp,
        KafkaFetchFlushExFW fetch)
    {
        final KafkaOffsetFW partition = fetch.partition();

        out.printf(verboseFormat, index, offset, timestamp,
                format("[fetch] %d %d %d %d",
                        partition.partitionId(),
                        partition.partitionOffset(),
                        partition.stableOffset(),
                        partition.latestOffset()));
    }

    private void onKafkaResetEx(
        final ResetFW reset)
    {
        final int offset = reset.offset() - HEADER_LENGTH;
        final long timestamp = reset.timestamp();
        final OctetsFW extension = reset.extension();

        final KafkaResetExFW kafkaResetEx = kafkaResetExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

        final int error = kafkaResetEx.error();

        out.printf(verboseFormat, index, offset, timestamp, format("error %d", error));
    }

    private void onMqttBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final MqttBeginExFW mqttBeginEx = mqttBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

        switch (mqttBeginEx.kind())
        {
        case MqttBeginExFW.KIND_PUBLISH:
            onMqttPublishBeginEx(offset, timestamp, mqttBeginEx.publish());
            break;
        case MqttBeginExFW.KIND_SUBSCRIBE:
            onMqttSubscribeBeginEx(offset, timestamp, mqttBeginEx.subscribe());
            break;
        case MqttBeginExFW.KIND_SESSION:
            onMqttSessionBeginEx(offset, timestamp, mqttBeginEx.session());
            break;
        }
    }

    private void onMqttPublishBeginEx(
        int offset,
        long timestamp,
        MqttPublishBeginExFW publish)
    {
        final String clientId = publish.clientId().asString();
        final String topic = publish.topic().asString();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[publish] %s %s", clientId, topic));
    }

    private void onMqttSubscribeBeginEx(
        int offset,
        long timestamp,
        MqttSubscribeBeginExFW subscribe)
    {
        final String clientId = subscribe.clientId().asString();
        final Array32FW<MqttTopicFilterFW> filters = subscribe.filters();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[subscribe] %s", clientId));
        filters.forEach(f -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s %d %d", f.pattern(), f.subscriptionId(), f.flags())));

    }

    private void onMqttSessionBeginEx(
        int offset,
        long timestamp,
        MqttSessionBeginExFW session)
    {
        final String clientId = session.clientId().asString();
        final int expiry = session.expiry();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[session] %s %d", clientId, expiry));
    }

    private void onMqttDataEx(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final OctetsFW extension = data.extension();

        final MqttDataExFW mqttDataEx = mqttDataExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

        switch (mqttDataEx.kind())
        {
        case MqttBeginExFW.KIND_PUBLISH:
            onMqttPublishDataEx(offset, timestamp, mqttDataEx.publish());
            break;
        case MqttBeginExFW.KIND_SUBSCRIBE:
            onMqttSubscribeDataEx(offset, timestamp, mqttDataEx.subscribe());
            break;
        }
    }

    private void onMqttPublishDataEx(
        int offset,
        long timestamp,
        MqttPublishDataExFW publish)
    {
        final int deferred = publish.deferred();
        final int flags = publish.flags();
        final int expiryInterval = publish.expiryInterval();
        final String contentType = publish.contentType().asString();
        final MqttPayloadFormat format = publish.format().get();
        final String responseTopic = publish.responseTopic().asString();
        final String correlation = asString(publish.correlation().bytes());
        final Array32FW<MqttUserPropertyFW> properties = publish.properties();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[publish] (%d) %d %d %s %s %s %s",
                deferred, flags, expiryInterval, contentType, format.name(), responseTopic, correlation));
        properties.forEach(u -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s %s ", u.key(), u.value())));
    }

    private void onMqttSubscribeDataEx(
        int offset,
        long timestamp,
        MqttSubscribeDataExFW subscribe)
    {
        final int deferred = subscribe.deferred();
        final String topic = subscribe.topic().asString();
        final int packetId = subscribe.packetId();
        final int flags = subscribe.flags();
        final Array32FW<Varuint32FW> subscriptionIds = subscribe.subscriptionIds();
        final int expiryInterval = subscribe.expiryInterval();
        final String contentType = subscribe.contentType().asString();
        final MqttPayloadFormat format = subscribe.format().get();
        final String responseTopic = subscribe.responseTopic().asString();
        final String correlation = asString(subscribe.correlation().bytes());
        final Array32FW<MqttUserPropertyFW> properties = subscribe.properties();

        out.printf(verboseFormat, index, offset, timestamp,
            format("[subscribe] (%d) %s %d %d %d %s %s %s %s",
                deferred, topic, packetId, flags, expiryInterval, contentType, format.name(), responseTopic, correlation));
        subscriptionIds.forEach(s -> out.printf(verboseFormat, index, offset, timestamp,
            format("Subscription ID: %d ", s.value())));
        properties.forEach(u -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s %s ", u.key(), u.value())));
    }

    private void onMqttFlushEx(
        final FlushFW flush)
    {
        final int offset = flush.offset() - HEADER_LENGTH;
        final long timestamp = flush.timestamp();
        final OctetsFW extension = flush.extension();

        final MqttFlushExFW mqttFlushEx = mqttFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());


        switch (mqttFlushEx.kind())
        {
        case MqttFlushExFW.KIND_PUBLISH:
            onMqttPublishFlushEx(offset, timestamp, mqttFlushEx.publish());
            break;
        case MqttFlushExFW.KIND_SUBSCRIBE:
            onMqttSubscribeFlushEx(offset, timestamp, mqttFlushEx.subscribe());
            break;
        }
    }

    private void onMqttPublishFlushEx(
        int offset,
        long timestamp,
        MqttPublishFlushExFW publish)
    {
        out.printf(verboseFormat, index, offset, timestamp, format("%d", publish.packetId()));
    }

    private void onMqttSubscribeFlushEx(
        int offset,
        long timestamp,
        MqttSubscribeFlushExFW subscribe)
    {
        final Array32FW<MqttTopicFilterFW> filters = subscribe.filters();

        filters.forEach(f -> out.printf(verboseFormat, index, offset, timestamp,
            format("%s %d %d", f.pattern(), f.subscriptionId(), f.flags())));
    }

    private void onAmqpBeginEx(
        final BeginFW begin)
    {
        final int offset = begin.offset() - HEADER_LENGTH;
        final long timestamp = begin.timestamp();
        final OctetsFW extension = begin.extension();

        final AmqpBeginExFW amqpBeginEx = amqpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        final String address = amqpBeginEx.address().asString();
        final String capabilities = amqpBeginEx.capabilities().toString();
        final String senderSettleMode = amqpBeginEx.senderSettleMode().toString();
        final String receiverSettleMode = amqpBeginEx.receiverSettleMode().toString();

        out.printf(verboseFormat, index, offset, timestamp, format("address: %s", address));
        out.printf(verboseFormat, index, offset, timestamp, format("capabilities: %s", capabilities));
        out.printf(verboseFormat, index, offset, timestamp, format("senderSettleMode: %s", senderSettleMode));
        out.printf(verboseFormat, index, offset, timestamp, format("receiverSettleMode: %s", receiverSettleMode));
    }

    private void onAmqpDataEx(
        final DataFW data)
    {
        final int offset = data.offset() - HEADER_LENGTH;
        final long timestamp = data.timestamp();
        final OctetsFW extension = data.extension();

        final AmqpDataExFW amqpDataEx = amqpDataExRO.wrap(extension.buffer(), extension.offset(), extension.limit());
        final int deferred = amqpDataEx.deferred();
        final long messageFormat = amqpDataEx.messageFormat();
        final int flags = amqpDataEx.flags();
        final AmqpPropertiesFW properties = amqpDataEx.properties();

        out.printf(verboseFormat, index, offset, timestamp, format("deferred: %d", deferred));
        out.printf(verboseFormat, index, offset, timestamp, format("deliveryTag: %s", amqpDataEx.deliveryTag()));
        out.printf(verboseFormat, index, offset, timestamp, format("messageFormat: %d", messageFormat));
        out.printf(verboseFormat, index, offset, timestamp, format("flags: %d", flags));

        amqpDataEx.annotations().forEach(a -> out.printf(verboseFormat, index, offset, timestamp,
            format("annotation: [key:%s] [value:%s]", a.key(), a.value())));
        if (properties.hasMessageId())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("messageId: %s", properties.messageId()));
        }
        if (properties.hasUserId())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("userId: %s", properties.userId()));
        }
        if (properties.hasTo())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("to: %s", properties.to().asString()));
        }
        if (properties.hasSubject())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("subject: %s", properties.subject().asString()));
        }
        if (properties.hasReplyTo())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("replyTo: %s", properties.replyTo().asString()));
        }
        if (properties.hasCorrelationId())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("correlationId: %s",
                properties.correlationId()));
        }
        if (properties.hasContentType())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("contentType: %s",
                properties.contentType().asString()));
        }
        if (properties.hasContentEncoding())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("contentEncoding: %s",
                properties.contentEncoding().asString()));
        }
        if (properties.hasAbsoluteExpiryTime())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("absoluteExpiryTime: %d",
                properties.absoluteExpiryTime()));
        }
        if (properties.hasCreationTime())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("creationTime: %d", properties.creationTime()));
        }
        if (properties.hasGroupId())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("groupId: %s", properties.groupId().asString()));
        }
        if (properties.hasGroupSequence())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("groupSequence: %d", properties.groupSequence()));
        }
        if (properties.hasReplyToGroupId())
        {
            out.printf(verboseFormat, index, offset, timestamp, format("replyToGroupId: %s",
                properties.replyToGroupId().asString()));
        }
        amqpDataEx.applicationProperties().forEach(a -> out.printf(verboseFormat, index, offset, timestamp,
            format("applicationProperty: [key:%s] [value:%s]", a.key(), a.value())));
    }

    private static String asString(
        OctetsFW value)
    {
        return value != null
            ? value.buffer().getStringWithoutLengthUtf8(value.offset(), value.sizeof())
            : "null";
    }
}
