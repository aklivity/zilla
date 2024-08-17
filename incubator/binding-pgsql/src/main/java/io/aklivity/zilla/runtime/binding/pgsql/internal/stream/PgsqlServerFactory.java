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
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageKind;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageKindFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlCompletedFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlQueryDataExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlReadyFlushExFW;
import io.aklivity.zilla.specs.binding.pgsql.internal.types.stream.PgsqlTypeFlushExFW;

public final class PgsqlServerFactory implements PgsqlStreamFactory
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


    private final PgsqlMessageFW messageRO = new PgsqlMessageFW();

    private final PgsqlMessageFW.Builder messageRW = new PgsqlMessageFW.Builder();

    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer frameBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<PgsqlBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final PgsqlServerDecoder decodePgsqlFrameType = this::decodePgsqlFrameType;
    private final PgsqlServerDecoder decodePgsqlQuery = this::decodePgsqlMessageQuery;
    private final PgsqlServerDecoder decodePgsqlPayload = this::decodePgsqlMessagePayload;
    private final PgsqlServerDecoder decodePgsqlIgnoreOne = this::decodePgsqlIgnoreOne;
    private final PgsqlServerDecoder decodePgsqlIgnoreAll = this::decodePgsqlIgnoreAll;

    private final EnumMap<PgsqlMessageKind, PgsqlServerDecoder> decodersByMessageKind;

    {
        final EnumMap<PgsqlMessageKind, PgsqlServerDecoder> decodersByMessageKind = new EnumMap<>(PgsqlMessageKind.class);
        decodersByMessageKind.put(PgsqlMessageKind.QUERY, decodePgsqlQuery);
        this.decodersByMessageKind = decodersByMessageKind;
    }

    public PgsqlServerFactory(
        PgsqlConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.frameBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.bufferPool = context.bufferPool();
        this.decodeMax = bufferPool.slotCapacity();

        this.bindings = new Long2ObjectHashMap<>();

        this.pgsqlTypeId = context.supplyTypeId(PgsqlBinding.NAME);
    }

    @FunctionalInterface
    private interface PgsqlServerDecoder
    {
        int decode(
            PgsqlServer server,
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
                newStream = new PgsqlServer(
                    network,
                    originId,
                    routedId,
                    initialId,
                    route.id)::onNetworkMessage;
            }
        }

        return newStream;
    }

    private final class PgsqlServer
    {
        private final PgsqlStream stream;
        private PgsqlServerDecoder decoder;

        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private long authorization;

        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
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

        private PgsqlServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);

            this.stream = new PgsqlStream(this, routedId, resolvedId);
            this.decoder = decodePgsqlFrameType;
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
            assert sequence >= initialSeq;
            assert maximum == 0;

            initialSeq = sequence;
            initialAck = acknowledge;

            state = PgsqlState.openingInitial(state);

            stream.doApplicationBegin(traceId, authorization, affinity);
        }

        private void onNetworkData(
            final DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + decodeMax)
            {
                cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();

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

            state = PgsqlState.closeInitial(state);

            cleanupDecodeSlotIfNecessary();

            stream.doApplicationEnd(traceId, authorization);
        }

        private void onNetworkAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = PgsqlState.closeInitial(state);

            stream.doApplicationAbort(traceId, authorization);
        }

        private void onNetworkReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = PgsqlState.closeReply(state);

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
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyBudgetId = budgetId;
            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            state = PgsqlState.openReply(state);

            stream.doApplicationWindow(traceId, authorization, budgetId, (int)(replySeq - replyAck), replyPad);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            doBegin(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, EMPTY_OCTETS);

            state = PgsqlState.openingReply(state);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);
            if (initialAckMax > initialAck || stream.initialMax > initialMax)
            {
                initialAck = initialAckMax;
                initialMax = stream.initialMax;
                initialPadding = paddingMin;
                assert initialAck <= initialSeq;

                state = PgsqlState.openInitial(state);

                final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .budgetId(budgetId)
                        .padding(initialPadding)
                        .build();

                network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            }
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
            state = PgsqlState.closeReply(state);

            doEnd(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeReply(state);

            doAbort(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closingInitial(state);

            doReset(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
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
            PgsqlServerDecoder previous = null;
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
                    decodeSlot = bufferPool.acquire(initialId);
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

            if (!PgsqlState.initialClosed(state))
            {
                doNetworkWindow(traceId, authorization, budgetId, decodeSlotReserved, initialPadding);
            }

            return progress;
        }

        private void onDecodeMessageQuery(
            long traceId,
            long authorization,
            int flags,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            Consumer<OctetsFW.Builder> queryEx = e -> e.set((b, o, l) -> dataExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .query(PgsqlQueryDataExFW.Builder::build)
                .build().sizeof());

            stream.doApplicationData(traceId, authorization, flags, buffer, offset, limit, queryEx);
        }

        private void doNetworkData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final int reserved = length + replyPad;

            doData(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                flags, budgetId, reserved, buffer, offset, length, EMPTY_EXTENSION);

            replySeq += reserved;
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
        private final PgsqlServer server;

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

        private int state;

        private PgsqlStream(
            PgsqlServer server,
            long originId,
            long routedId)
        {
            this.server = server;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
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

            state = PgsqlState.openingReply(state);

            server.doNetworkBegin(traceId, authorization, affinity);
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
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;
            assert budgetId == server.replyBudgetId;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                server.cleanupNetwork(traceId, authorization);
            }
            else
            {
                final OctetsFW extension = data.extension();
                final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);

                final PgsqlDataExFW pgsqlDataEx = dataEx != null && dataEx.typeId() == pgsqlTypeId ?
                        extension.get(pgsqlDataExRO::tryWrap) : null;

                if (pgsqlDataEx.kind() == PgsqlDataExFW.KIND_ROW)
                {
                    final OctetsFW payload = data.payload();

                    if (payload != null)
                    {
                        doEncodeRow(traceId, authorization, payload);
                    }
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
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;

            assert replyAck <= replySeq;

            if (replySeq > replySeq + replyMax)
            {
                server.cleanupNetwork(traceId, authorization);
            }

            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);

            final PgsqlFlushExFW pgsqlFlushEx = flushEx != null && flushEx.typeId() == pgsqlTypeId ?
                    extension.get(pgsqlFlushExRO::tryWrap) : null;

            assert pgsqlFlushEx != null;

            switch (pgsqlFlushEx.kind())
            {
            case PgsqlFlushExFW.KIND_TYPE:
                doEncodeType(traceId, authorization, pgsqlFlushEx.type());
                break;
            case PgsqlFlushExFW.KIND_COMPLETION:
                doEncodeCompleted(traceId, authorization, pgsqlFlushEx.completion());
                break;
            case PgsqlFlushExFW.KIND_READY:
                doEncodeReady(traceId, authorization, pgsqlFlushEx.ready());
                break;
            default:
                assert false;
                break;
            }
        }

        private void onApplicationEnd(
            final EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = PgsqlState.closeReply(state);

            server.doNetworkEnd(traceId, authorization);
        }

        private void onApplicationAbort(
            final AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = PgsqlState.closeReply(state);

            server.doNetworkAbort(traceId, authorization);
        }

        private void onApplicationReset(
            final ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = PgsqlState.closeInitial(state);

            server.doNetworkReset(traceId, authorization);
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
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialBudgetId = budgetId;
            initialAck = acknowledge;
            initialMax = maximum;
            initialPad = padding;

            assert initialAck <= initialSeq;

            server.doNetworkWindow(traceId, authorization, budgetId, (int)(initialSeq - initialAck), initialPad);
        }

        private void doApplicationBegin(
                long traceId,
                long authorization,
                long affinity)
        {
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

            application = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                    this::onApplicationMessage);

            application.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
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
            doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);

            state = PgsqlState.closeInitial(state);
        }

        private void doApplicationAbort(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeInitial(state);

            doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
        }

        private void doApplicationReset(
            long traceId,
            long authorization)
        {
            state = PgsqlState.closeReply(state);

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
            long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
            if (replyAckMax > replyAck || server.replyMax > replyMax)
            {
                replyAck = replyAckMax;
                replyMax = server.replyMax;
                assert replyAck <= replySeq;

                int replyPad = paddingMin;

                state = PgsqlState.openReply(state);

                doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, replyPad);
            }
        }

        private void doEncodeType(
            long traceId,
            long authorization,
            PgsqlTypeFlushExFW type)
        {
            final MutableInteger typeOffset = new MutableInteger(0);

            PgsqlMessageFW messageType = messageRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.TYPE))
                .length(0)
                .build();
            typeOffset.getAndAdd(messageType.limit());

            frameBuffer.putShort(typeOffset.value, (short) type.columns().fieldCount());
            typeOffset.getAndAdd(Short.BYTES);

            type.columns().forEach(c ->
            {
                final DirectBuffer nameBuffer = c.name().value();
                final int nameSize = nameBuffer.capacity();

                frameBuffer.putBytes(typeOffset.value, nameBuffer, 0, nameSize);
                typeOffset.getAndAdd(nameSize);
                frameBuffer.putByte(typeOffset.value, (byte) 0x00);
                typeOffset.getAndAdd(Byte.BYTES);
                frameBuffer.putInt(typeOffset.value, c.tableOid());
                typeOffset.getAndAdd(Integer.BYTES);
                frameBuffer.putShort(typeOffset.value, c.index());
                typeOffset.getAndAdd(Short.BYTES);
                frameBuffer.putInt(typeOffset.value, c.typeOid());
                typeOffset.getAndAdd(Integer.BYTES);
                frameBuffer.putShort(typeOffset.value, c.length());
                typeOffset.getAndAdd(Short.BYTES);
                frameBuffer.putShort(typeOffset.value, c.modifier());
                typeOffset.getAndAdd(Short.BYTES);
                frameBuffer.putShort(typeOffset.value, (short) c.format().get().value());
                typeOffset.getAndAdd(Short.BYTES);
            });

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, frameBuffer, 0, typeOffset.value);
        }

        private void doEncodeRow(
            long traceId,
            long authorization,
            OctetsFW row)
        {
            final DirectBuffer rowBuffer = row.value();
            final int rowSize = rowBuffer.capacity();

            int rowOffset = 0;

            PgsqlMessageFW messageRow = messageRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.ROW))
                .length(rowSize + Integer.BYTES)
                .build();
            rowOffset += messageRow.limit();

            frameBuffer.putBytes(rowOffset, rowBuffer, 0, rowSize);
            rowOffset += rowSize;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, frameBuffer, 0, rowOffset);
        }

        private void doEncodeCompleted(
            long traceId,
            long authorization,
            PgsqlCompletedFlushExFW completion)
        {
            final DirectBuffer tagBuffer = completion.tag().value();
            final int tagSize = tagBuffer.capacity();

            int completionOffset = 0;

            PgsqlMessageFW messageCompleted = messageRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.COMPLETION))
                .length(tagSize + Integer.BYTES)
                .build();
            completionOffset += messageCompleted.limit();

            frameBuffer.putBytes(completionOffset, tagBuffer, 0, tagSize);
            completionOffset += tagSize;
            frameBuffer.putByte(completionOffset, (byte) 0x00);
            completionOffset += Byte.BYTES;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, frameBuffer, 0, completionOffset);
        }

        private void doEncodeReady(
            long traceId,
            long authorization,
            PgsqlReadyFlushExFW ready)
        {
            int readyOffset = 0;

            PgsqlMessageFW messageReady = messageRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .kind(k -> k.set(PgsqlMessageKind.READY))
                .length(ready.status().sizeof() + Integer.BYTES)
                .build();
            readyOffset += messageReady.limit();

            frameBuffer.putByte(messageReady.limit(), (byte) ready.status().get().value());
            readyOffset += Byte.BYTES;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, frameBuffer, 0, readyOffset);
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

    private int decodePgsqlFrameType(
        PgsqlServer server,
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

            final PgsqlServerDecoder decoder = decodersByMessageKind.getOrDefault(kind.get(), decodePgsqlIgnoreOne);
            server.decoder = decoder;
            progress = pgsqlMessage.limit();
        }

        return progress;
    }

    private int decodePgsqlMessageQuery(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int payloadSize = payloadRemaining.get();
        final int length = Math.min(payloadSize, limit - offset);

        final int flags = payloadSize == length ? FLAGS_COMP : FLAGS_INIT;

        server.onDecodeMessageQuery(traceId, authorization, flags, buffer, offset, offset + length);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() >= 0;

        server.decoder = payloadSize == length
            ? decodePgsqlFrameType
            : decodePgsqlPayload;

        return length;
    }

    private int decodePgsqlMessagePayload(
        PgsqlServer server,
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

        server.stream.doApplicationData(traceId, authorization, flags, buffer, offset, offset + limit,
            EMPTY_EXTENSION);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() >= 0;

        if (payloadSize == length)
        {
            server.decoder = decodePgsqlFrameType;
        }

        return length;
    }

    private int decodePgsqlIgnoreOne(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final PgsqlMessageFW messageType = messageRO.wrap(buffer, offset, limit);
        final int progress = messageType.limit() + messageType.length();

        server.decoder = decodePgsqlFrameType;
        return progress;
    }

    private int decodePgsqlIgnoreAll(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

}
