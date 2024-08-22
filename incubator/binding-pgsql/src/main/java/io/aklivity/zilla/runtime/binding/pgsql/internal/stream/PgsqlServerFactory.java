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

import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
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
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlAuthenticationMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlBackendKeyMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlCancelRequestMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlSslRequestFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlSslResponseFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.codec.PgsqlStartupMessageFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlBeginExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlCompletedFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlDataExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlParameterFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlReadyFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlStatus;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.PgsqlTypeFlushExFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.pgsql.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class PgsqlServerFactory implements PgsqlStreamFactory
{
    private static final Byte MESSAGE_TYPE_TYPE = 'T';
    private static final Byte MESSAGE_TYPE_QUERY = 'Q';
    private static final Byte MESSAGE_TYPE_DATA_ROW = 'D';
    private static final Byte MESSAGE_TYPE_COMPLETION = 'C';
    private static final Byte MESSAGE_TYPE_READY = 'Z';
    private static final Byte MESSAGE_TYPE_TERMINATE = 'X';
    private static final Byte MESSAGE_TYPE_PARAMETER_STATUS = 'S';

    private static final int SSL_REQUEST_CODE = 80877103;
    private static final int CANCEL_REQUEST_CODE = 80877102;
    private static final int END_OF_FIELD = 0x00;

    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_CONT = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_COMP = 0x03;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(new byte[0]);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final MutableInteger payloadRemaining = new MutableInteger(0);
    private final MutableInteger progress = new MutableInteger(0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final PgsqlDataExFW pgsqlDataExRO = new PgsqlDataExFW();
    private final PgsqlFlushExFW pgsqlFlushExRO = new PgsqlFlushExFW();

    private final PgsqlBeginExFW.Builder beginExRW = new PgsqlBeginExFW.Builder();
    private final PgsqlDataExFW.Builder dataExRW = new PgsqlDataExFW.Builder();

    private final PgsqlMessageFW messageRO = new PgsqlMessageFW();
    private final PgsqlSslRequestFW sslRequestRO = new PgsqlSslRequestFW();
    private final PgsqlStartupMessageFW startupMessageRO = new PgsqlStartupMessageFW();
    private final PgsqlCancelRequestMessageFW cancelReqMessageRO = new PgsqlCancelRequestMessageFW();

    private final PgsqlMessageFW.Builder messageRW = new PgsqlMessageFW.Builder();
    private final PgsqlSslResponseFW.Builder sslResponseRW = new PgsqlSslResponseFW.Builder();
    private final PgsqlAuthenticationMessageFW.Builder authMessageRW = new PgsqlAuthenticationMessageFW.Builder();
    private final PgsqlBackendKeyMessageFW.Builder backendKeyMessageRW = new PgsqlBackendKeyMessageFW.Builder();

    private final Array32FW.Builder<PgsqlParameterFW.Builder, PgsqlParameterFW> parametersRW =
        new Array32FW.Builder<>(new PgsqlParameterFW.Builder(), new PgsqlParameterFW());

    private final BufferPool bufferPool;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer messageBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;

    private final int decodeMax;

    private final Long2ObjectHashMap<PgsqlBindingConfig> bindings;
    private final int pgsqlTypeId;

    private final PgsqlServerDecoder decodePgsqlInitial = this::decodePgsqlInitial;
    private final PgsqlServerDecoder decodePgsqlSslRequest = this::decodePgsqlSslRequest;
    private final PgsqlServerDecoder decodePgsqlStartupMessage = this::decodePgsqlStartupMessage;
    private final PgsqlServerDecoder decodePgsqlCancelRequest = this::decodePgsqlCancelRequest;
    private final PgsqlServerDecoder decodePgsqlMessageType = this::decodePgsqlMessageType;
    private final PgsqlServerDecoder decodePgsqlQuery = this::decodePgsqlMessageQuery;
    private final PgsqlServerDecoder decodePgsqlPayload = this::decodePgsqlMessagePayload;
    private final PgsqlServerDecoder decodePgsqlTermination = this::decodePgsqlMessageTerminator;
    private final PgsqlServerDecoder decodePgsqlIgnoreOne = this::decodePgsqlIgnoreOne;
    private final PgsqlServerDecoder decodePgsqlIgnoreAll = this::decodePgsqlIgnoreAll;

    private final Int2ObjectHashMap<PgsqlServerDecoder> decodersByType;
    {
        Int2ObjectHashMap<PgsqlServerDecoder> decodersByType = new Int2ObjectHashMap();
        decodersByType.put(MESSAGE_TYPE_QUERY, decodePgsqlQuery);
        decodersByType.put(MESSAGE_TYPE_TERMINATE, decodePgsqlTermination);
        this.decodersByType = decodersByType;
    }

    public PgsqlServerFactory(
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
        final long affinity = begin.affinity();
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
                    affinity,
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
        private final long affinity;

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
            long affinity,
            long resolvedId)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.initialMax = decodeMax;

            this.stream = new PgsqlStream(this, routedId, resolvedId);
            this.decoder = decodePgsqlInitial;
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

            doNetworkWindow(traceId, authorization, 0L, 0, 0);
            doNetworkBegin(traceId, authorization, affinity);
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

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            final long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);

            if (initialAckMax > initialAck ||
                stream.initialMax > initialMax ||
                initialMax > stream.initialMax)
            {
                initialAck = initialAckMax;
                initialMax = Math.max(stream.initialMax, initialMax);
                initialPadding = paddingMin;
                assert initialAck <= initialSeq;

                state = PgsqlState.openInitial(state);

                doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, initialPadding);
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

        public void onDecodeSslRequest(
            long traceId,
            long authorization)
        {
            PgsqlSslResponseFW sslResponse = sslResponseRW.wrap(messageBuffer, 0, messageBuffer.capacity()).build();
            doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, sslResponse.limit());
        }

        private void onDecodeStartup(
            long traceId,
            long authorization,
            Array32FW<PgsqlParameterFW> parameters)
        {
            Consumer<OctetsFW.Builder> beginEx = e -> e.set((b, o, l) -> beginExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .parameters(parameters)
                .build().sizeof());
            stream.doApplicationBegin(traceId, authorization, beginEx);

            doNetworkWindow(traceId, authorization, authorization, decodeSlotReserved, initialPadding);

            PgsqlAuthenticationMessageFW authMessage = authMessageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .build();
            doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, authMessage.limit());

            PgsqlBackendKeyMessageFW backendKeyMessage =
                backendKeyMessageRW.wrap(messageBuffer, 0, messageBuffer.capacity()).build();
            doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, backendKeyMessage.limit());

            doEncodeParamStatus(traceId, "client_encoding", "UTF8");
            doEncodeParamStatus(traceId, "standard_conforming_strings", "on");
            doEncodeParamStatus(traceId, "server_version", "1.0.0");
            doEncodeParamStatus(traceId, "application_name", "zilla");

            int progress = 0;
            PgsqlMessageFW message = messageRW.wrap(messageBuffer, progress, messageBuffer.capacity())
                .type(MESSAGE_TYPE_READY)
                .length(Integer.BYTES + Byte.BYTES)
                .build();
            progress = message.limit();

            messageBuffer.putByte(progress, (byte) PgsqlStatus.IDLE.value());
            progress += Byte.BYTES;

            doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, progress);
        }

        private void doEncodeParamStatus(
            long traceId,
            String name,
            String value)
        {
            int statusOffset = 0;

            PgsqlMessageFW status = messageRW.wrap(messageBuffer, statusOffset, messageBuffer.capacity())
                .type(MESSAGE_TYPE_PARAMETER_STATUS)
                .length(0)
                .build();
            statusOffset = status.limit();

            messageBuffer.putBytes(statusOffset, name.getBytes());
            statusOffset += name.length();
            messageBuffer.putByte(statusOffset, (byte) END_OF_FIELD);
            statusOffset += Byte.BYTES;

            messageBuffer.putBytes(statusOffset, value.getBytes());
            statusOffset += value.length();
            messageBuffer.putByte(statusOffset, (byte) END_OF_FIELD);
            statusOffset += Byte.BYTES;

            messageBuffer.putInt(Byte.BYTES, statusOffset - Byte.BYTES, BIG_ENDIAN);

            doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, statusOffset);
        }

        private void onDecodeMessageQuery(
            long traceId,
            long authorization,
            int flags,
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            Consumer<OctetsFW.Builder> queryEx = e -> e.set((b, o, l) -> dataExRW.wrap(b, o, l)
                .typeId(pgsqlTypeId)
                .query(q -> q.deferred(deferred))
                .build().sizeof());

            stream.doApplicationData(traceId, authorization, flags, buffer, offset, limit, queryEx);
        }

        private void onDecodeMessageTermination(
            long traceId,
            long authorization)
        {
            stream.doApplicationEnd(traceId, authorization);
            doNetworkEnd(traceId, authorization);
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

            state = PgsqlState.openingReply(state);

            doApplicationWindow(traceId, authorization, server.replyBudgetId, 0, 0);
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
                doEncodeCompletion(traceId, authorization, pgsqlFlushEx.completion());
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

            if (!PgsqlState.closed(server.state))
            {
                doEncodeTerminate(traceId, authorization);

                state = PgsqlState.closeReply(state);

                server.doNetworkEnd(traceId, authorization);
            }
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
            Consumer<OctetsFW.Builder> extension)
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
                    .affinity(server.affinity)
                    .extension(extension)
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
            final long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
            if (PgsqlState.replyOpening(state) &&
                (replyAckMax > replyAck || server.replyMax > replyMax))
            {
                replyAck = replyAckMax;
                replyMax = server.replyMax;
                assert replyAck <= replySeq;

                state = PgsqlState.openReply(state);

                doWindow(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, paddingMin);
            }
        }

        private void doEncodeType(
            long traceId,
            long authorization,
            PgsqlTypeFlushExFW type)
        {
            final MutableInteger typeOffset = new MutableInteger(0);

            PgsqlMessageFW messageType = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_TYPE)
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
                messageBuffer.putInt(typeOffset.value, c.tableOid(), BIG_ENDIAN);
                typeOffset.getAndAdd(Integer.BYTES);
                messageBuffer.putShort(typeOffset.value, c.index(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
                messageBuffer.putInt(typeOffset.value, c.typeOid(), BIG_ENDIAN);
                typeOffset.getAndAdd(Integer.BYTES);
                messageBuffer.putShort(typeOffset.value, c.length(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
                messageBuffer.putInt(typeOffset.value, c.modifier(), BIG_ENDIAN);
                typeOffset.getAndAdd(Integer.BYTES);
                messageBuffer.putShort(typeOffset.value, (short) c.format().get().value(), BIG_ENDIAN);
                typeOffset.getAndAdd(Short.BYTES);
            });

            messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_TYPE)
                .length(typeOffset.get() - Byte.BYTES)
                .build();

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, typeOffset.value);
        }

        private void doEncodeRow(
            long traceId,
            long authorization,
            OctetsFW row)
        {
            final DirectBuffer rowBuffer = row.value();
            final int rowSize = rowBuffer.capacity();

            int rowOffset = 0;

            PgsqlMessageFW messageRow = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_DATA_ROW)
                .length(rowSize + Integer.BYTES)
                .build();
            rowOffset += messageRow.limit();

            messageBuffer.putBytes(rowOffset, rowBuffer, 0, rowSize);
            rowOffset += rowSize;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, rowOffset);
        }

        private void doEncodeCompletion(
            long traceId,
            long authorization,
            PgsqlCompletedFlushExFW completion)
        {
            final DirectBuffer tagBuffer = completion.tag().value();
            final int tagSize = tagBuffer.capacity();

            int completionOffset = 0;

            PgsqlMessageFW messageCompleted = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_COMPLETION)
                .length(Integer.BYTES + tagSize)
                .build();
            completionOffset += messageCompleted.limit();

            messageBuffer.putBytes(completionOffset, tagBuffer, 0, tagSize);
            completionOffset += tagSize;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, completionOffset);
        }

        private void doEncodeReady(
            long traceId,
            long authorization,
            PgsqlReadyFlushExFW ready)
        {
            int readyOffset = 0;

            PgsqlMessageFW messageReady = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_READY)
                .length(ready.status().sizeof() + Integer.BYTES)
                .build();
            readyOffset += messageReady.limit();

            messageBuffer.putByte(messageReady.limit(), (byte) ready.status().get().value());
            readyOffset += Byte.BYTES;

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, readyOffset);
        }

        private void doEncodeTerminate(
            long traceId,
            long authorization)
        {
            PgsqlMessageFW messageReady = messageRW.wrap(messageBuffer, 0, messageBuffer.capacity())
                .type(MESSAGE_TYPE_TERMINATE)
                .length(Integer.BYTES)
                .build();

            server.doNetworkData(traceId, authorization, FLAGS_COMP, 0L, messageBuffer, 0, messageReady.limit());
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

    private int decodePgsqlInitial(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final PgsqlSslRequestFW pgsqlSslRequest = sslRequestRO.tryWrap(buffer, offset, limit);
        final PgsqlCancelRequestMessageFW cancelRequest = cancelReqMessageRO.tryWrap(buffer, offset, limit);
        final PgsqlStartupMessageFW startupMessage = startupMessageRO.tryWrap(buffer, offset, limit);

        if (pgsqlSslRequest != null &&
            pgsqlSslRequest.code() == SSL_REQUEST_CODE)
        {
            server.decoder = decodePgsqlSslRequest;
        }
        else if (cancelRequest != null &&
                cancelRequest.code() == CANCEL_REQUEST_CODE)
        {
            server.decoder = decodePgsqlCancelRequest;
        }
        else if (startupMessage != null)
        {
            server.decoder = decodePgsqlStartupMessage;
        }
        else
        {
            server.decoder = decodePgsqlIgnoreAll;
        }

        return offset;
    }

    private int decodePgsqlSslRequest(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        PgsqlSslRequestFW sslRequest = sslRequestRO.wrap(buffer, offset, limit);

        server.onDecodeSslRequest(traceId, authorization);
        server.decoder = decodePgsqlStartupMessage;

        return sslRequest.limit();
    }

    private int decodePgsqlStartupMessage(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        progress.set(offset);

        PgsqlStartupMessageFW startupMessage = startupMessageRO.tryWrap(buffer, offset, limit);

        if (startupMessage != null)
        {
            progress.set(startupMessage.limit());
            Array32FW.Builder<PgsqlParameterFW.Builder, PgsqlParameterFW> parameterBuilder =
                parametersRW.wrap(messageBuffer, 0, messageBuffer.capacity());
            final int maxLimit = startupMessage.length() + progress.value - startupMessage.sizeof() - Integer.BYTES;
            while (progress.value < maxLimit)
            {
                final int nameLength = getLengthOfString(buffer, progress.value);
                final int valueLength = getLengthOfString(buffer, progress.value + nameLength);

                parameterBuilder.item(i -> i
                    .name(buffer, progress.value, nameLength)
                    .value(buffer, progress.value + nameLength, valueLength));

                progress.addAndGet(nameLength + valueLength);

                if (buffer.getByte(progress.value) == (byte) END_OF_FIELD)
                {
                    progress.addAndGet(Byte.BYTES);
                }
            }

            server.onDecodeStartup(traceId, authorization, parametersRW.build());
            server.decoder = decodePgsqlMessageType;
        }

        return progress.value;
    }

    private int decodePgsqlCancelRequest(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        PgsqlSslRequestFW sslRequest = sslRequestRO.wrap(buffer, offset, limit);

        server.onDecodeSslRequest(traceId, authorization);
        server.decoder = decodePgsqlStartupMessage;

        return sslRequest.limit();
    }

    private int decodePgsqlMessageType(
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
            final int type = pgsqlMessage.type();

            final PgsqlServerDecoder decoder = decodersByType.getOrDefault(type, decodePgsqlIgnoreOne);
            server.decoder = decoder;
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
        int progressOffset = offset;

        final PgsqlMessageFW pgsqlQuery = messageRO.tryWrap(buffer, progressOffset, limit);
        if (pgsqlQuery != null)
        {
            progressOffset = pgsqlQuery.limit();

            final int querySize = pgsqlQuery.length() - Integer.BYTES;
            payloadRemaining.set(querySize);

            final int length = Math.min(payloadRemaining.value, limit - progressOffset);

            if (length > 0)
            {
                final int flags = querySize == length ? FLAGS_COMP : FLAGS_INIT;
                final int deferred = querySize - length;

                server.onDecodeMessageQuery(traceId, authorization, flags, deferred,
                    buffer, progressOffset, progressOffset + length);
                progressOffset += length;
                payloadRemaining.set(querySize - length);

                assert payloadRemaining.get() >= 0;

                server.decoder = querySize == length
                    ? decodePgsqlMessageType
                    : decodePgsqlPayload;
            }
        }

        return progressOffset;
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

        final int maxLimit = offset + length;
        server.stream.doApplicationData(traceId, authorization, flags, buffer, offset, maxLimit, EMPTY_EXTENSION);
        payloadRemaining.set(payloadSize - length);

        assert payloadRemaining.get() >= 0;

        if (payloadSize == length)
        {
            server.decoder = decodePgsqlMessageType;
        }

        return maxLimit;
    }

    private int decodePgsqlMessageTerminator(
        PgsqlServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int progressOffset = offset;

        final PgsqlMessageFW pgsqlTerminate = messageRO.tryWrap(buffer, offset, limit);

        if (pgsqlTerminate != null)
        {
            server.onDecodeMessageTermination(traceId, authorization);

            server.decoder = decodePgsqlIgnoreAll;
            progressOffset = limit;
        }

        return progressOffset;
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

        server.decoder = decodePgsqlMessageType;
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

    private int getLengthOfString(
        DirectBuffer buffer,
        int offset)
    {
        int length = -1;
        loop:
        for (int progress = offset; progress < buffer.capacity(); progress++)
        {
            if (buffer.getByte(progress) == END_OF_FIELD)
            {
                length = progress - offset + 1;
                break loop;
            }
        }
        return length;
    }
}
