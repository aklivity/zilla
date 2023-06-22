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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaWithConfig.EVENT_ID_ETAG_ONLY;
import static io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaWithConfig.EVENT_ID_KEY64_AND_ETAG;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.SseKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.config.SseKafkaWithResult;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class SseKafkaProxyFactory implements SseKafkaStreamFactory
{
    private static final int INIT_FLAG = 0x02;
    private static final String SSE_TYPE_NAME = "sse";
    private static final String KAFKA_TYPE_NAME = "kafka";

    private static final String8FW EVENT_TYPE_MESSAGE = new String8FW(null);
    private static final String8FW EVENT_TYPE_DELETE = new String8FW("delete");

    private static final String8FW HEADER_NAME_ETAG = new String8FW("etag");

    private final OctetsFW emptyExRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

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
    private final SseBeginExFW sseBeginExRO = new SseBeginExFW();

    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();

    private final SseDataExFW.Builder sseDataExRW = new SseDataExFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();

    private final SseKafkaIdHelper sseEventId = new SseKafkaIdHelper();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int sseTypeId;
    private final int kafkaTypeId;
    private final int kafkaReplyMin;

    private final Long2ObjectHashMap<SseKafkaBindingConfig> bindings;

    public SseKafkaProxyFactory(
        SseKafkaConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.sseTypeId = context.supplyTypeId(SSE_TYPE_NAME);
        this.kafkaTypeId = context.supplyTypeId(KAFKA_TYPE_NAME);
        this.kafkaReplyMin = config.maximumKeyLength();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        SseKafkaBindingConfig sseKafkaBinding = new SseKafkaBindingConfig(binding);
        bindings.put(binding.id, sseKafkaBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sse)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final OctetsFW extension = begin.extension();
        final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);

        final SseKafkaBindingConfig binding = bindings.get(routedId);

        SseKafkaRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(authorization, sseBeginEx);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            final long resolvedId = route.id;
            final SseKafkaWithResult resolved = route.with
                    .map(r -> r.resolve(authorization, sseBeginEx, sseEventId))
                    .orElse(null);

            newStream = new SseProxy(
                    sse,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    resolved)::onSseMessage;
        }

        return newStream;
    }

    private final class SseProxy
    {
        private final MessageConsumer sse;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final KafkaProxy delegate;
        private final SseKafkaWithResult resolved;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private SseProxy(
            MessageConsumer sse,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            SseKafkaWithResult resolved)
        {
            this.sse = sse;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new KafkaProxy(routedId, resolvedId, this);
            this.resolved = resolved;
        }

        private void onSseMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onSseBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onSseData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onSseEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onSseAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onSseReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onSseWindow(window);
                break;
            }
        }

        private void onSseBegin(
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
            state = SseKafkaState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaBegin(traceId, authorization, affinity, resolved);
        }

        private void onSseData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            doSseReset(traceId);
            delegate.doKafkaAbort(traceId, authorization);
        }

        private void onSseEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = SseKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaEnd(traceId, initialSeq, authorization);
        }

        private void onSseAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = SseKafkaState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doKafkaAbort(traceId, authorization);
        }

        private void onSseReset(
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
            state = SseKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaReset(traceId);
        }

        private void onSseWindow(
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
            state = SseKafkaState.openReply(state);

            assert replyAck <= replySeq;

            delegate.doKafkaWindow(traceId, authorization, budgetId, padding, kafkaReplyMin, capabilities);
        }

        private void doSseBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            replySeq = delegate.replySeq;
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;
            state = SseKafkaState.openingReply(state);

            doBegin(sse, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity);
        }

        private void doSseData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(sse, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doSseFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            replySeq = delegate.replySeq;

            doFlush(sse, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doSseAbort(
            long traceId,
            long authorization)
        {
            if (!SseKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = SseKafkaState.closeReply(state);

                doAbort(sse, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doSseEnd(
            long traceId,
            long authorization)
        {
            if (!SseKafkaState.replyClosed(state))
            {
                replySeq = delegate.replySeq;
                state = SseKafkaState.closeReply(state);

                doEnd(sse, originId, routedId, replyId, replySeq, replyAck, replyMax,
                      traceId, authorization);
            }
        }

        private void doSseWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(sse, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, 0, capabilities);
        }

        private void doSseReset(
            long traceId)
        {
            if (!SseKafkaState.initialClosed(state))
            {
                state = SseKafkaState.closeInitial(state);

                doReset(sse, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId);
            }
        }
    }

    final class KafkaProxy
    {
        private MessageConsumer kafka;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final SseProxy delegate;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaProxy(
            long originId,
            long routedId,
            SseProxy delegate)
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
            long affinity,
            SseKafkaWithResult resolved)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = SseKafkaState.openingInitial(state);

            kafka = newKafkaStream(this::onKafkaMessage, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, resolved);
        }

        private void doKafkaEnd(
            long traceId,
            long sequence,
            long authorization)
        {
            if (!SseKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = SseKafkaState.closeInitial(state);

                doEnd(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doKafkaAbort(
            long traceId,
            long authorization)
        {
            if (!SseKafkaState.initialClosed(state))
            {
                initialSeq = delegate.initialSeq;
                initialAck = delegate.initialAck;
                initialMax = delegate.initialMax;
                state = SseKafkaState.closeInitial(state);

                doAbort(kafka, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
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
            state = SseKafkaState.openingReply(state);

            assert replyAck <= replySeq;

            delegate.doSseBegin(traceId, authorization, affinity);
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

            System.out.println(String.format("Sse budget ondata %d", replyMax - (int)(replySeq - replyAck)));

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doKafkaReset(traceId);
                delegate.doSseAbort(traceId, authorization);
            }
            else
            {
                String8FW encodedId = null;
                OctetsFW key = null;
                final int flags = data.flags();
                final OctetsFW payload = data.payload();

                if ((flags & INIT_FLAG) != 0x00)
                {
                    final OctetsFW extension = data.extension();
                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx =
                        dataEx != null && dataEx.typeId() == kafkaTypeId ? extension.get(kafkaDataExRO::tryWrap) : null;
                    final KafkaMergedDataExFW kafkaMergedDataEx =
                        kafkaDataEx != null && kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED ? kafkaDataEx.merged() : null;
                    final Array32FW<KafkaOffsetFW> progress = kafkaMergedDataEx != null ? kafkaMergedDataEx.progress() : null;
                    key = kafkaMergedDataEx != null ? kafkaMergedDataEx.key().value() : null;
                    final Array32FW<KafkaHeaderFW> headers = kafkaMergedDataEx != null ? kafkaMergedDataEx.headers() : null;
                    final KafkaHeaderFW etag = headers.matchFirst(h -> HEADER_NAME_ETAG.value().equals(h.name().value()));

                    switch (delegate.resolved.eventId())
                    {
                    case EVENT_ID_KEY64_AND_ETAG:
                        encodedId = sseEventId.encodeKeyAndProgress(key, progress, etag);
                        break;
                    case EVENT_ID_ETAG_ONLY:
                        encodedId = sseEventId.encodeProgressOnly(progress, etag);
                        break;
                    }
                }

                final String8FW eventType = payload == null ? EVENT_TYPE_DELETE : EVENT_TYPE_MESSAGE;
                final Flyweight sseDataEx = encodedId == null
                        ? emptyExRO
                        : sseDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(sseTypeId)
                            .id(encodedId)
                            .type(eventType)
                            .build();

                final OctetsFW eventData = payload == null && key != null ? sseEventId.encodeKey(key) : payload;
                delegate.doSseData(traceId, authorization, budgetId, reserved, flags, eventData, sseDataEx);
            }
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
            state = SseKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doSseEnd(traceId, authorization);
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

            delegate.doSseFlush(traceId, authorization, budgetId, reserved);
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
            state = SseKafkaState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doSseAbort(traceId, authorization);
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
            state = SseKafkaState.openInitial(state);

            assert initialAck <= initialSeq;

            delegate.doSseWindow(authorization, traceId, budgetId, padding, capabilities);
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

            delegate.doSseReset(traceId);
        }

        private void doKafkaReset(
            long traceId)
        {
            if (!SseKafkaState.replyClosed(state))
            {
                state = SseKafkaState.closeReply(state);

                doReset(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId);
            }
        }

        private void doKafkaWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int minimum,
            int capabilities)
        {
            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;

            doWindow(kafka, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, minimum, capabilities);

            System.out.println(String.format("Sse budget doKafkaWindow %d", replyMax - (int)(replySeq - replyAck)));
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
        long affinity,
        SseKafkaWithResult resolved)
    {
        final KafkaBeginExFW kafkaBeginEx =
            kafkaBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(kafkaTypeId)
                .merged(m -> m.capabilities(c -> c.set(KafkaCapabilities.FETCH_ONLY))
                              .topic(resolved.topic())
                              .partitions(resolved.partitions())
                              .filters(resolved::filters))
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
