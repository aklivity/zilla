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
package io.aklivity.zilla.runtime.binding.pgsql.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.Objects.requireNonNull;

import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongLongConsumer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.pgsql.internal.PgsqlBinding;
import io.aklivity.zilla.runtime.binding.pgsql.internal.PgsqlConfiguration;
import io.aklivity.zilla.runtime.binding.pgsql.internal.config.PgsqlBindingConfig;
import io.aklivity.zilla.runtime.binding.pgsql.internal.config.PgsqlRouteConfig;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageKind;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageKindFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlRowDescriptionFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlColumnInfoFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlFormat;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlQueryDataExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlStatus;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlTypeFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;


public final class PgsqlClientFactory implements PgsqlStreamFactory
{
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_CONT = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMP = 0x03;

    private final MutableInteger payloadRemaining = new MutableInteger(0);

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

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final PgsqlDataExFW pgsqlDataExRO = new PgsqlDataExFW();
    private final PgsqlFlushExFW pgsqlFlushExRO = new PgsqlFlushExFW();

    private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();
    private final PgsqlFlushExFW.Builder flushExRW = new PgsqlFlushExFW.Builder();
    private final Array32FW.Builder<PgsqlColumnInfoFW.Builder, PgsqlColumnInfoFW> columnsRW =
        new Array32FW.Builder<>(new PgsqlColumnInfoFW.Builder(), new PgsqlColumnInfoFW());

    private final PgsqlMessageFW messageRO = new PgsqlMessageFW();
    private final PgsqlRowDescriptionFW rowDescriptionRO = new PgsqlRowDescriptionFW();

    private final PgsqlMessageFW.Builder messageRW = new PgsqlMessageFW.Builder();

    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer messageBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<PgsqlBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final PgsqlClientDecoder decodePgsqlMessageType = this::decodePgsqlMessageKind;
    private final PgsqlClientDecoder decodePgsqlType = this::decodePgsqlMessageType;
    private final PgsqlClientDecoder decodePgsqlRow = this::decodePgsqlMessageRow;
    private final PgsqlClientDecoder decodePgsqlCompletion = this::decodePgsqlMessageCompletion;
    private final PgsqlClientDecoder decodePgsqlReady = this::decodePgsqlMessageReady;
    private final PgsqlClientDecoder decodePgsqlPayload = this::decodePgsqlMessagePayload;
    private final PgsqlClientDecoder decodePgsqlIgnoreOne = this::decodePgsqlIgnoreOne;
    private final PgsqlClientDecoder decodePgsqlIgnoreAll = this::decodePgsqlIgnoreAll;

    private final EnumMap<PgsqlMessageKind, PgsqlClientDecoder> decodersByMessageKind;

    {
        final EnumMap<PgsqlMessageKind, PgsqlClientDecoder> decodersByMessageKind = new EnumMap<>(PgsqlMessageKind.class);
        decodersByMessageKind.put(PgsqlMessageKind.TYPE, decodePgsqlType);
        decodersByMessageKind.put(PgsqlMessageKind.ROW, decodePgsqlRow);
        decodersByMessageKind.put(PgsqlMessageKind.COMPLETION, decodePgsqlCompletion);
        decodersByMessageKind.put(PgsqlMessageKind.READY, decodePgsqlReady);
        this.decodersByMessageKind = decodersByMessageKind;
    }

    public PgsqlClientFactory(
        PgsqlConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.messageBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.decodeMax = bufferPool.slotCapacity();

        this.bindings = new Long2ObjectHashMap<>();

        this.pgsqlTypeId = context.supplyTypeId(PgsqlBinding.NAME);
    }

    @FunctionalInterface
    private interface PgsqlClientDecoder
    {
        int decode(
            PgsqlClient client,
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        PgsqlBindingConfig pgsqlBinding = new PgsqlBindingConfig(binding);
        bindings.put(binding.id, pgsqlBinding);
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        PgsqlBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            PgsqlRouteConfig route = binding.resolve(authorization);

            if (route != null)
            {
                newStream = new PgsqlStream(
                    network,
                    originId,
                    routedId,
                    initialId,
                    route.id)::onApplicationMessage;
            }
        }

        return newStream;
    }

    private final class PgsqlClient
    {
        private final PgsqlStream stream;
        private PgsqlClientDecoder decoder;

        private MessageConsumer network;
        private final long originId;
        private final long routedId;
        private long authorization;

        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        public long initialBudgetId;
        private int initialPadding;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBudgetId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int state;

        private PgsqlClient(
            PgsqlStream stream,
            long originId,
            long routedId)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            this.stream = stream;
            this.decoder = decodePgsqlMessageType;
        }

        private void onNetworkMessage(
            final int msgTypeId,
            final DirectBuffer buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onNetworkBegin(
            final BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge == sequence;
            assert sequence >= replySeq;
            assert maximum == 0;

            replySeq = sequence;
            replyAck = acknowledge;

            state = PgsqlState.openingReply(state);

            stream.doApplicationBegin(traceId, authorization, affinity);
        }

        private void onNetworkData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            int reserved = data.reserved();
            authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + decodeMax)
            {
                cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetworkEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = PgsqlState.closeReply(state);

            cleanupDecodeSlotIfNecessary();

            stream.doApplicationEnd(traceId, authorization);
        }

        private void onNetworkAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = PgsqlState.closeReply(state);

            stream.doApplicationAbort(traceId, authorization);
        }

        private void onNetworkReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = PgsqlState.closeInitial(state);

            stream.doApplicationReset(traceId, authorization);
        }

        private void onNetworkWindow(
            final WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            initialAck = acknowledge;
            initialMax = maximum;
            initialBudgetId = budgetId;
            initialPadding = padding;

            assert initialAck <= initialSeq;

            state = PgsqlState.openInitial(state);

            stream.doApplicationWindow(traceId, authorization, budgetId, (int)(initialSeq - initialAck), initialPadding);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            state = PgsqlState.openingInitial(state);

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .affinity(affinity)
                    .extension(EMPTY_OCTETS)
                    .build();

            network = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                    this::onNetworkMessage);

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
            if (replyAckMax > replyAck || stream.replyMax > replyMax)
            {
                replyAck = replyAckMax;
                replyMax = stream.replyMax;
                replyPad = paddingMin;
                assert replyAck <= replySeq;

                state = PgsqlState.openReply(state);

                doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, replyPad);
            }
        }

        private void  doNetworkData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final int reserved = length + replyPad;

            doData(network, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                flags, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

            initialSeq += reserved;
        }

        private void doNetworkResetAndAbort(
            long traceId,
            long authorization)
        {
            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeInitial(state);

            doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeReply(state);

            doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closingInitial(state);

            doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);

            cleanupDecodeSlotIfNecessary();
        }

        private int decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            PgsqlClientDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, buffer, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(replyId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = bufferPool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int)((long) reserved * (limit - progress) / (limit - offset));
                }
            }
            else
            {
                cleanupDecodeSlotIfNecessary();
            }

            if (!PgsqlState.replyClosed(state))
            {
                doNetworkWindow(traceId, authorization, budgetId, decodeSlotReserved, replyPad);
            }

            return progress;
        }

        private void onDecodeMessageType(
            long traceId,
            long authorization,
            Array32FW<PgsqlColumnInfoFW> columns)
        {
            Consumer<OctetsFW.Builder> typeEx = e -> e.set((b, o, l) -> flushExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .type(t -> t.columns(columns))
                .build().sizeof());

            stream.doApplicationFlush(traceId, authorization, decodeSlotReserved, typeEx);
        }

        private void onDecodeMessageCompletion(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            Consumer<OctetsFW.Builder> completionEx = e -> e.set((b, o, l) -> flushExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .completion(c -> c.tag(buffer, offset, length))
                .build().sizeof());

            stream.doApplicationFlush(traceId, authorization, decodeSlotReserved, completionEx);
        }

        private void onDecodeMessageReady(
            long traceId,
            long authorization,
            PgsqlStatus status)
        {
            Consumer<OctetsFW.Builder> readyEx = e -> e.set((b, o, l) -> flushExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .ready(r -> r.status(s -> s.set(status)))
                .build().sizeof());

            stream.doApplicationFlush(traceId, authorization, decodeSlotReserved, readyEx);
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            cleanup(traceId, authorization, this::doNetworkResetAndAbort);
        }

        private void cleanup(
            long traceId,
            long authorization,
            LongLongConsumer cleanupHandler)
        {
            cleanupHandler.accept(traceId, authorization);
            stream.doApplicationAbortAndReset(traceId, authorization);
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                decodeSlotReserved = 0;
            }
        }
    }

    private final class PgsqlStream
    {
        private final PgsqlClient client;

        private MessageConsumer application;

        private final long initialId;
        private final long replyId;
        private final long originId;
        private final long routedId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBudgetId;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPadding;
        private long replyBudgetId;

        private int state;

        private PgsqlStream(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);

            this.client = new PgsqlClient(this, originId, resolvedId);
        }

        private void onApplicationMessage(
            final int msgTypeId,
            final DirectBuffer buffer,
            final int index,
            final int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onApplicationBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onApplicationData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onApplicationEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onApplicationAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onApplicationFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onApplicationReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onApplicationWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onApplicationBegin(
            final BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            state = PgsqlState.openingInitial(state);

            client.doNetworkBegin(traceId, authorization, affinity);
        }

        private void onApplicationData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;
            assert budgetId == client.initialBudgetId;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                client.cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW extension = data.extension();
                final OctetsFW payload = data.payload();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);

                final PgsqlDataExFW pgsqlDataEx = dataEx != null && dataEx.typeId() == pgsqlTypeId ?
                        extension.get(pgsqlDataExRO::tryWrap) : null;

                if (pgsqlDataEx != null &&
                    pgsqlDataEx.kind() == PgsqlDataExFW.KIND_QUERY)
                {
                    PgsqlQueryDataExFW query = pgsqlDataEx.query();
                    doEncodeQuery(traceId, authorization, query.deferred(), payload);
                }
                else
                {
                    client.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, payload.value(), 0, payload.sizeof());
                }
            }
        }

        private void onApplicationFlush(
            final FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            if (initialSeq > initialSeq + initialMax)
            {
                client.cleanupNetwork(traceId, authorization);
            }

            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);

            final PgsqlFlushExFW pgsqlFlushEx = flushEx != null && flushEx.typeId() == pgsqlTypeId ?
                    extension.get(pgsqlFlushExRO::tryWrap) : null;

            assert pgsqlFlushEx != null;

            if (pgsqlFlushEx.kind() == PgsqlFlushExFW.KIND_TYPE)
            {
                doEncodeType(traceId, authorization, pgsqlFlushEx.type());
            }
        }

        private void onApplicationEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = PgsqlState.closeInitial(state);

            client.doNetworkEnd(traceId, authorization);
        }

        private void onApplicationAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = PgsqlState.closeInitial(state);

            client.doNetworkAbort(traceId, authorization);
        }

        private void onApplicationReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = PgsqlState.closeReply(state);

            client.doNetworkReset(traceId, authorization);
        }

        private void onApplicationWindow(
            final WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;
            assert maximum + acknowledge >= replyMax + replyAck;

            replyBudgetId = budgetId;
            replyAck = acknowledge;
            replyMax = maximum;
            replyPadding = padding;

            assert replyAck <= replySeq;

            client.doNetworkWindow(traceId, authorization, budgetId, (int)(replySeq - replyAck), replyPadding);
        }

        private void doApplicationBegin(
                long traceId,
                long authorization,
                long affinity)
        {
            doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);

            state = PgsqlState.openingReply(state);
        }

        private void doApplicationData(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit,
            Consumer<OctetsFW.Builder> extension)
        {
            final int length = limit - offset;
            final int reserved = length + initialPad;

            doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                flags, initialBudgetId, reserved, buffer, offset, length, extension);

            initialSeq += reserved;
            assert initialSeq <= initialAck + initialMax;
        }

        private void doApplicationEnd(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeInitial(state);

            doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbort(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeReply(state);

            doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationReset(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeInitial(state);

            doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationAbortAndReset(
            long traceId,
            long authorization)
        {
            doApplicationAbort(traceId, authorization);
            doApplicationReset(traceId, authorization);
        }

        private void doApplicationWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);
            if (initialAckMax > initialAck || client.initialMax > initialMax)
            {
                initialAck = initialAckMax;
                initialMax = client.initialMax;
                assert initialAck <= initialSeq;

                int initialPad = paddingMin;

                state = PgsqlState.openInitial(state);

                doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, initialPad);
            }
        }

        private void doEncodeType(
            long traceId,
            long authorization,
            PgsqlTypeFlushExFW type)
        {
            final MutableInteger typeOffset = new MutableInteger(0);

            PgsqlMessageFW messageType = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.TYPE))
                .length(0)
                .build();
            typeOffset.getAndAdd(messageType.limit());

            messageBuffer.putShort(typeOffset.value, (short) type.columns().fieldCount(), BIG_ENDIAN);
            typeOffset.getAndAdd(Short.BYTES);

            type.columns().forEach(c ->
            {
                final DirectBuffer nameBuffer = c.name().value();
                final int nameSize = nameBuffer.capacity();

                messageBuffer.putBytes(typeOffset.value, nameBuffer, 0, nameSize);
                typeOffset.getAndAdd(nameSize);
                messageBuffer.putByte(typeOffset.value, (byte) 0x00);
                typeOffset.getAndAdd(Byte.BYTES);
                messageBuffer.putInt(typeOffset.value, c.tableOid(), BIG_ENDIAN);
                typeOffset.getAndAdd(Integer.BYTES);
                messageBuffer.putShort(typeOffset.value, c.index(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
                messageBuffer.putInt(typeOffset.value, c.typeOid(), BIG_ENDIAN);
                typeOffset.getAndAdd(Integer.BYTES);
                messageBuffer.putShort(typeOffset.value, c.length(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
                messageBuffer.putShort(typeOffset.value, c.modifier(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
                messageBuffer.putShort(typeOffset.value, (short) c.format().get().value(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
            });

            client.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, typeOffset.value);
        }

        private void doEncodeQuery(
            long traceId,
            long authorization,
            int deferred,
            OctetsFW query)
        {
            final DirectBuffer queryBuffer = query.value();
            final int rowSize = queryBuffer.capacity();

            int queryOffset = 0;

            PgsqlMessageFW messageRow = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.QUERY))
                .length(rowSize + Integer.BYTES + deferred)
                .build();
            queryOffset += messageRow.limit();

            messageBuffer.putBytes(queryOffset, queryBuffer, 0, rowSize);
            queryOffset += rowSize;

            client.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, queryOffset);
        }

        public void doApplicationFlush(
            long traceId,
            long authorization,
            int reserved,
            Consumer<OctetsFW.Builder> extension)
        {
            replySeq = client.replySeq;

            doFlush(application, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId,
                    authorization, replyBudgetId, reserved, extension);
        }
    }

    private void doBegin(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final long affinity,
        final OctetsFW extension)
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
                .extension(extension)
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final int flags,
        final long budgetId,
        final int reserved,
        DirectBuffer buffer,
        int offset,
        int length,
        Consumer<OctetsFW.Builder> extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                .payload(buffer, offset, length)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doFlush(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final long budgetId,
        final int reserved,
        Consumer<OctetsFW.Builder> extension)
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
                .extension(extension)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doAbort(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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
                .extension(extension)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doEnd(
        final MessageConsumer receiver,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
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
                .extension(extension)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doReset(
        final MessageConsumer sender,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        final long authorization,
        final OctetsFW extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        final MessageConsumer sender,
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge,
        final int maximum,
        final long traceId,
        long authorization,
        final long budgetId,
        final int padding)
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
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private int decodePgsqlMessageKind(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final PgsqlMessageFW pgsqlMessage = messageRO.tryWrap(buffer, offset, limit);

        if (pgsqlMessage != null)
        {
            final PgsqlMessageKindFW kind = pgsqlMessage.kind();
            final int length = pgsqlMessage.length();
            payloadRemaining.set(length - Integer.BYTES);

            final PgsqlClientDecoder decoder = decodersByMessageKind.getOrDefault(kind.get(), decodePgsqlIgnoreOne);
            client.decoder = decoder;
            progress = pgsqlMessage.limit();
        }

        return progress;
    }

    private int decodePgsqlMessageType(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = limit - offset;

        int progressOffset = offset;

        short fieldCount = buffer.getShort(progressOffset);
        progressOffset += Short.BYTES;

        columnsRW.wrap(messageBuffer, 0, messageBuffer.capacity());

        columns:
        for (int i = 0; i < fieldCount; i++)
        {
            final int nameOffset = progressOffset;
            final int nameLength = getLengthOfString(buffer, progressOffset);

            if (nameLength == -1)
            {
                break columns;
            }

            progressOffset += nameLength + Byte.BYTES;

            PgsqlRowDescriptionFW description = rowDescriptionRO.tryWrap(buffer, progressOffset, limit);

            if (description == null)
            {
                break columns;
            }

            columnsRW.item(c -> c
                .name(buffer, nameOffset, nameOffset)
                .tableOid(description.tableOid())
                .index(description.index())
                .typeOid(description.typeOid())
                .length(description.modifier())
                .format(f -> f.set(PgsqlFormat.valueOf(description.format()))));
        }

        Array32FW<PgsqlColumnInfoFW> columns = columnsRW.build();

        if (columns.fieldCount() == fieldCount)
        {
            client.onDecodeMessageType(traceId, authorization, columns);
            payloadRemaining.set(payloadSize - length);

            assert payloadRemaining.get() == 0;

            client.decoder = decodePgsqlMessageType;
        }

        return progressOffset;
    }

    private int decodePgsqlMessageRow(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = limit - offset;
        int progress = offset + length;

        client.onDecodeMessageCompletion(traceId, authorization, buffer, offset, length);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() == 0;

        client.decoder = decodePgsqlMessageType;

        return progress;
    }

    private int decodePgsqlMessageCompletion(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = limit - offset;
        int progress = offset + length;

        client.onDecodeMessageCompletion(traceId, authorization, buffer, offset, length);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() == 0;

        client.decoder = decodePgsqlMessageType;

        return progress;
    }

    private int decodePgsqlMessageReady(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = limit - offset;
        int progress = offset + length;

        client.onDecodeMessageReady(traceId, authorization, PgsqlStatus.valueOf(buffer.getShort(offset)));
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() == 0;

        client.decoder = decodePgsqlMessageType;

        return progress;
    }

    private int decodePgsqlMessagePayload(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = Math.min(payloadSize, limit - offset);

        final int flags = payloadSize == length ? FLAGS_FIN : FLAGS_CONT;

        client.stream.doApplicationData(traceId, authorization, flags, buffer, offset, offset + limit,
            EMPTY_EXTENSION);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() >= 0;

        if (payloadSize == length)
        {
            client.decoder = decodePgsqlMessageType;
        }

        return length;
    }

    private int decodePgsqlIgnoreOne(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final PgsqlMessageFW messageType = messageRO.wrap(buffer, offset, limit);
        final int progress = messageType.limit() + messageType.length();

        client.decoder = decodePgsqlMessageType;
        return progress;
    }

    private int decodePgsqlIgnoreAll(
        PgsqlClient client,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    public int getLengthOfString(
        DirectBuffer buffer,
        int startingOffset)
    {
        int index = -1;
        for (int i = startingOffset; i < buffer.capacity(); i++)
        {
            if (buffer.getByte(i) == 0x00)
            {
                index = i - 1;
            }
        }

        return index;
    }

}
