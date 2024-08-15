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
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.pgsql.internal.PgsqlBinding;
import io.aklivity.zilla.runtime.binding.pgsql.internal.PgsqlConfiguration;
import io.aklivity.zilla.runtime.binding.pgsql.internal.config.PgsqlBindingConfig;
import io.aklivity.zilla.runtime.binding.pgsql.internal.config.PgsqlRouteConfig;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlType;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlTypeFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class PgsqlServerFactory implements PgsqlStreamFactory
{
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);

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
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final PgsqlMessageFW messageRO = new PgsqlMessageFW();

    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<PgsqlBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final PgsqlServerDecoder decodePgsqlFrameType = this::decodePgsqlFrameType;
    private final PgsqlServerDecoder decodePgsqlQuery = this::decodePgsqlMessageQuery;
    private final PgsqlServerDecoder decodePgsqlIgnoreOne = this::decodePgsqlIgnoreOne;
    private final PgsqlServerDecoder decodePgsqlIgnoreAll = this::decodePgsqlIgnoreAll;

    private final EnumMap<PgsqlType, PgsqlServerDecoder> decodersByMessageType;

    {
        final EnumMap<PgsqlType, PgsqlServerDecoder> decodersByMessageType = new EnumMap<>(PgsqlType.class);
        decodersByMessageType.put(PgsqlType.QUERY, decodePgsqlQuery);
        this.decodersByMessageType = decodersByMessageType;
    }

    public PgsqlServerFactory(
        PgsqlConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
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
                    route.id)::onMessage;
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

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

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
        }

        private void onMessage(
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetworkFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onWindow(window);
                break;
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onChallenge(challenge);
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

            stream.doAppBegin(traceId, authorization, affinity);
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

        private void onNetworkFlush(
            final FlushFW flush)
        {
            final long originId = flush.originId();
            final long routedId = flush.routedId();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            doFlush(network, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, budgetId, reserved, extension);
        }

        private void onNetworkEnd(
            final EndFW end)
        {
            final long originId = end.originId();
            final long routedId = end.routedId();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            doEnd(network, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onNetworkAbort(
            final AbortFW abort)
        {
            final long originId = abort.originId();
            final long routedId = abort.routedId();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            doAbort(network, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onNetworkReset(
            final ResetFW reset)
        {
            final long originId = reset.originId();
            final long routedId = reset.routedId();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            doReset(network, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onWindow(
            final WindowFW window)
        {
            final long originId = window.originId();
            final long routedId = window.routedId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            doWindow(network, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    budgetId, padding);
        }

        private void onChallenge(
            ChallengeFW challenge)
        {
            final long originId = challenge.originId();
            final long routedId = challenge.routedId();
            final long sequence = challenge.sequence();
            final long acknowledge = challenge.acknowledge();
            final int maximum = challenge.maximum();
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            doChallenge(network, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int maximum)
        {
            doWindow(network, originId, routedId, initialId, initialSeq, initialAck, maximum,
                    traceId, budgetId, padding);
        }

        private void doNetworkResetAndAbort(
            long traceId,
            long authorization)
        {
            doNetworkReset(traceId, authorization);
            doNetworkAbort(traceId, authorization);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            cleanupEncodeSlotIfNecessary();
            doAbort(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, EMPTY_OCTETS);
            state = PgsqlState.closeReply(state);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlotIfNecessary();
            doReset(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, EMPTY_OCTETS);
            state = PgsqlState.closeInitial(state);
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
                doNetworkWindow(traceId, authorization, budgetId, decodeSlotReserved, initialMax);
            }

            return progress;
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
            //TODO: add cleanup
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

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
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

        private void onMessage(
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onApplicationChallenge(challenge);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onApplicationBegin(
            final BeginFW begin)
        {
            final long originId = begin.originId();
            final long routedId = begin.routedId();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            doBegin(application, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, affinity, extension);
        }

        private void onApplicationData(
            final DataFW data)
        {
            final long originId = data.originId();
            final long routedId = data.routedId();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            doData(application, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, flags, budgetId, reserved, payload, extension);
        }

        private void onApplicationFlush(
            final FlushFW flush)
        {
            final long originId = flush.originId();
            final long routedId = flush.routedId();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            doFlush(application, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, budgetId, reserved, extension);
        }

        private void onApplicationEnd(
            final EndFW end)
        {
            final long originId = end.originId();
            final long routedId = end.routedId();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            doEnd(application, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onApplicationAbort(
            final AbortFW abort)
        {
            final long originId = abort.originId();
            final long routedId = abort.routedId();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            doAbort(application, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onApplicationReset(
            final ResetFW reset)
        {
            final long originId = reset.originId();
            final long routedId = reset.routedId();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final OctetsFW extension = reset.extension();

            doReset(application, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void onApplicationWindow(
            final WindowFW window)
        {
            final long originId = window.originId();
            final long routedId = window.routedId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            doWindow(application, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    budgetId, padding);
        }

        private void onApplicationChallenge(
            ChallengeFW challenge)
        {
            final long originId = challenge.originId();
            final long routedId = challenge.routedId();
            final long sequence = challenge.sequence();
            final long acknowledge = challenge.acknowledge();
            final int maximum = challenge.maximum();
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            doChallenge(application, originId, routedId, initialId, sequence, acknowledge, maximum, traceId,
                    authorization, extension);
        }

        private void doAppBegin(
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

            application =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), this::onMessage);

            application.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            int maskingKey,
            OctetsFW payload)
        {
            final int reserved = payload.sizeof() + initialPad;

            final int capacity = payload.sizeof();
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(initialBudgetId)
                    .reserved(reserved)
                    .payload(payload)
                    .extension(EMPTY_OCTETS)
                    .build();

            application.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += reserved;
            assert initialSeq <= initialAck + initialMax;
        }

        private void doAppEnd(
            long traceId,
            long authorization,
            short code)
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(EMPTY_OCTETS)
                    .build();

            application.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void doAppAbort(
            long traceId,
            long authorization,
            short code)
        {
            // TODO: WsAbortEx
            final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .build();

            application.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(reserved)
                    .build();

            application.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .build();

            application.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .extension(extension)
                    .build();

            application.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void doAppWindow(
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

                final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(replyId)
                        .sequence(replySeq)
                        .acknowledge(replyAck)
                        .maximum(replyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .budgetId(budgetId)
                        .padding(replyPad)
                        .build();

                application.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
            }
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
        final OctetsFW payload,
        final OctetsFW extension)
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
                .payload(payload)
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
        final OctetsFW extension)
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
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doChallenge(
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
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

        sender.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
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
        final PgsqlMessageFW pgsqlMessage = messageRO.tryWrap(buffer, offset, limit);

        if (pgsqlMessage != null)
        {
            final PgsqlTypeFW type = pgsqlMessage.type();
            final int length = pgsqlMessage.length();
            final PgsqlServerDecoder decoder = decodersByMessageType.getOrDefault(type, decodePgsqlIgnoreOne);

            if (limit - pgsqlMessage.limit() >= length)
            {
                server.decoder = decoder;
            }
        }

        return offset;
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
        final PgsqlMessageFW pgsqlMessage = messageRO.tryWrap(buffer, offset, limit);

        if (pgsqlMessage != null)
        {
            final PgsqlTypeFW type = pgsqlMessage.type();
            final int length = pgsqlMessage.length();
            final PgsqlServerDecoder decoder = decodersByMessageType.getOrDefault(type, decodePgsqlIgnoreOne);

            if (limit - pgsqlMessage.limit() >= length)
            {
                server.decoder = decoder;
            }
        }

        return offset;
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
