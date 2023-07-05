/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static java.time.Instant.now;

import java.nio.ByteOrder;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.MqttKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaHeaderHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config.MqttKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormat;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttPayloadFormatFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishBeginExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.MqttPublishDataExFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class MqttKafkaPublishFactory implements BindingHandler
{
    private static final KafkaAckMode KAFKA_DEFAULT_ACK_MODE = KafkaAckMode.LEADER_ONLY;
    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final MqttBeginExFW mqttBeginExRO = new MqttBeginExFW();
    private final MqttDataExFW mqttDataExRO = new MqttDataExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();

    private final MqttDataExFW.Builder mqttDataExRW = new MqttDataExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> kafkaHeadersRW =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW());
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer keyBuffer;
    private final MutableDirectBuffer headerIntValueBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer kafkaHeadersBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MqttKafkaHeaderHelper helper;
    private final int mqttTypeId;
    private final int kafkaTypeId;
    private final Signaler signaler;
    private final LongFunction<MqttKafkaBindingConfig> supplyBinding;

    private String kafkaMessagesTopic;
    private KafkaKeyFW key;

    private OctetsFW[] topicNameHeaders;
    private OctetsFW clientIdOctets;
    private String16FW binaryFormat;
    private String16FW textFormat;

    public MqttKafkaPublishFactory(
        MqttKafkaConfiguration config,
        EngineContext context,
        LongFunction<MqttKafkaBindingConfig> supplyBinding)
    {
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.keyBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.headerIntValueBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.kafkaHeadersBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.helper = new MqttKafkaHeaderHelper();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.binaryFormat = new String16FW(MqttPayloadFormat.BINARY.name());
        this.textFormat = new String16FW(MqttPayloadFormat.TEXT.name());
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer mqtt)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        final OctetsFW extension = begin.extension();
        final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginExRO::tryWrap);

        assert mqttBeginEx.kind() == MqttBeginExFW.KIND_PUBLISH;
        final MqttPublishBeginExFW mqttPublishBeginEx = mqttBeginEx.publish();
        String topicName = mqttPublishBeginEx.topic().asString();
        assert topicName != null;

        String[] topicHeaders = topicName.split("/");
        topicNameHeaders = new OctetsFW[topicHeaders.length];
        for (int i = 0; i < topicHeaders.length; i++)
        {
            String16FW topicHeader = new String16FW(topicHeaders[i]);
            topicNameHeaders[i] = new OctetsFW().wrap(topicHeader.value(), 0, topicHeader.length());
        }
        clientIdOctets = new OctetsFW()
            .wrap(mqttPublishBeginEx.clientId().value(), 0, mqttPublishBeginEx.clientId().length());
        final DirectBuffer topicNameBuffer = mqttPublishBeginEx.topic().value();
        key = new KafkaKeyFW.Builder()
            .wrap(keyBuffer, 0, keyBuffer.capacity())
            .length(topicNameBuffer.capacity())
            .value(topicNameBuffer, 0, topicNameBuffer.capacity())
            .build();

        final MqttKafkaBindingConfig binding = supplyBinding.apply(routedId);


        final MqttKafkaRouteConfig resolved = binding != null ? binding.resolve(authorization) : null;
        kafkaMessagesTopic = binding.messagesTopic();

        MessageConsumer newStream = null;

        if (resolved != null)
        {
            final long resolvedId = resolved.id;
            newStream = new MqttPublishProxy(mqtt, originId, routedId, initialId, resolvedId)::onMqttMessage;
        }

        return newStream;
    }

    private final class MqttPublishProxy
    {
        private final MessageConsumer mqtt;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private MqttPublishProxy(
            MessageConsumer mqtt,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.mqtt = mqtt;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new KafkaProxy(originId, resolvedId, this);
        }

        private void onMqttMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMqttBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMqttData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMqttEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMqttAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMqttReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMqttWindow(window);
                break;
            }
        }

        private void onMqttBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            state = MqttKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaBegin(traceId, authorization, affinity);
        }

        private void onMqttData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            Flyweight kafkaDataEx = emptyRO;
            MqttDataExFW mqttDataEx = null;
            if (extension.sizeof() > 0)
            {
                mqttDataEx = extension.get(mqttDataExRO::tryWrap);
            }

            assert mqttDataEx.kind() == MqttDataExFW.KIND_PUBLISH;
            final MqttPublishDataExFW mqttPublishDataEx = mqttDataEx.publish();
            kafkaHeadersRW.wrap(kafkaHeadersBuffer, 0, kafkaHeadersBuffer.capacity());

            for (OctetsFW topicHeader : topicNameHeaders)
            {
                addHeader(helper.kafkaTopicHeaderName, topicHeader);
            }

            addHeader(helper.kafkaLocalHeaderName, clientIdOctets);

            if (mqttPublishDataEx.expiryInterval() != -1)
            {
                addHeader(helper.kafkaTimeoutHeaderName, mqttPublishDataEx.expiryInterval() * 1000);
            }

            if (mqttPublishDataEx.contentType().asString() != null)
            {
                addHeader(helper.kafkaContentTypeHeaderName, mqttPublishDataEx.contentType());
            }

            if (payload.sizeof() != 0 && mqttPublishDataEx.format() != null)
            {
                addHeader(helper.kafkaFormatHeaderName, mqttPublishDataEx.format());
            }

            if (mqttPublishDataEx.responseTopic().asString() != null)
            {
                addHeader(helper.kafkaReplyToHeaderName, mqttPublishDataEx.responseTopic());
            }

            if (mqttPublishDataEx.correlation().bytes() != null)
            {
                addHeader(helper.kafkaCorrelationHeaderName, mqttPublishDataEx.correlation().bytes());
            }

            mqttPublishDataEx.properties().forEach(property ->
                addHeader(property.key(), property.value()));

            final int deferred = mqttPublishDataEx.deferred();
            kafkaDataEx = kafkaDataExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m
                    .deferred(deferred)
                    .timestamp(now().toEpochMilli())
                    .partition(p -> p.partitionId(-1).partitionOffset(-1))
                    .key(b -> b.set(key))
                    .headers(kafkaHeadersRW.build()))
                .build();

            //TODO: do this onMqttData for subscribe
            //            doMqttReset(traceId);
            //            delegate.doKafkaAbort(traceId, authorization);
            delegate.doKafkaData(traceId, authorization, budgetId, reserved, flags, payload, kafkaDataEx);
        }


        private void onMqttEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaEnd(traceId, initialSeq, authorization);
        }

        private void onMqttAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = MqttKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaAbort(traceId, authorization);
        }

        private void onMqttReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaReset(traceId);
        }

        private void onMqttWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;
            state = MqttKafkaState.openReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaWindow(traceId, authorization, budgetId, padding, capabilities);
        }

        private void doMqttBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            replySeq = delegate.replySeq;
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            state = MqttKafkaState.openingReply(state);

            doBegin(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity);
        }

        private void doMqttData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doMqttFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId, reserved);
        }

        private void doMqttAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = MqttKafkaState.closeReply(state);

                doAbort(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doMqttEnd(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = MqttKafkaState.closeReply(state);

                doEnd(mqtt, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
            }
        }

        private void doMqttWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, 0, capabilities);
        }

        private void doMqttReset(
            long traceId)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                state = MqttKafkaState.closeInitial(state);

                doReset(mqtt, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    private void addHeader(
        OctetsFW key,
        int value)
    {
        headerIntValueBuffer.putInt(0, value, ByteOrder.BIG_ENDIAN);
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(4);
            h.value(headerIntValueBuffer, 0, 4);
        });
    }

    private void addHeader(
        OctetsFW key,
        OctetsFW value)
    {
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(value.sizeof());
            h.value(value);
        });
    }

    private void addHeader(
        OctetsFW key,
        MqttPayloadFormatFW format)
    {
        String16FW value = format.get() == MqttPayloadFormat.BINARY ? binaryFormat : textFormat;
        addHeader(key, value);
    }

    private void addHeader(
        OctetsFW key,
        String16FW value)
    {
        DirectBuffer buffer = value.value();
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.sizeof());
            h.name(key);
            h.valueLen(value.length());
            h.value(buffer, 0, buffer.capacity());
        });
    }

    private void addHeader(String16FW key, String16FW value)
    {
        DirectBuffer keyBuffer = key.value();
        DirectBuffer valueBuffer = value.value();
        kafkaHeadersRW.item(h ->
        {
            h.nameLen(key.length());
            h.name(keyBuffer, 0, keyBuffer.capacity());
            h.valueLen(value.length());
            h.value(valueBuffer, 0, valueBuffer.capacity());
        });
    }


    final class KafkaProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MqttPublishProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private KafkaProxy(
            long originId,
            long routedId,
            MqttPublishProxy delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doKafkaBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = MqttKafkaState.openingInitial(state);

            kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, affinity);
        }

        private void doKafkaData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, payload, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!MqttKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = MqttKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
            }
        }

        private void onKafkaMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onKafkaBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onKafkaData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onKafkaEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onKafkaAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onKafkaFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onKafkaWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onKafkaReset(reset);
                break;
            }
        }

        private void onKafkaBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = MqttKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            delegate.doMqttBegin(traceId, authorization, affinity);
        }

        private void onKafkaData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;
            doKafkaReset(traceId);
            delegate.doMqttAbort(traceId, authorization);
        }

        private void onKafkaEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doMqttEnd(traceId, authorization);
        }

        private void onKafkaFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            delegate.doMqttFlush(traceId, authorization, budgetId, reserved);
        }

        private void onKafkaAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = MqttKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doMqttAbort(traceId, authorization);
        }

        private void onKafkaWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long authorization = window.authorization();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;
            assert maximum >= delegate.initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            state = MqttKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doMqttWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onKafkaReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();

            assert acknowledge <= sequence;
            assert acknowledge >= delegate.initialAck;

            delegate.initialAck = acknowledge;

            assert delegate.initialAck <= delegate.initialSeq;

            delegate.doMqttReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!MqttKafkaState.replyClosed(state))
            {
                state = MqttKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            replyPad = delegate.replyPad;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding, replyPad, capabilities);
        }
    }


    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private MessageConsumer newKafkaStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(KafkaCapabilities.PRODUCE_ONLY))
                    .topic(kafkaMessagesTopic)
                    .partitionsItem(p -> p.partitionId(-1).partitionOffset(-2L))
                    .ackMode(b -> b.set(KAFKA_DEFAULT_ACK_MODE)))
                .build();


        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(kafkaBeginEx.buffer(), kafkaBeginEx.offset(), kafkaBeginEx.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int minimum,
        int capabilities)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .minimum(minimum)
            .capabilities(capabilities)
            .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
