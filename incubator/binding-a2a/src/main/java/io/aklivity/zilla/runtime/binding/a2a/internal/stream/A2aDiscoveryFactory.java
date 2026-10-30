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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;

import java.util.List;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.a2a.internal.A2aConfiguration;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentCards;
import io.aklivity.zilla.runtime.binding.a2a.internal.config.A2aProxyBindingConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.discovery.A2aDiscoveryCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.discovery.A2aDiscoveryMatch;
import io.aklivity.zilla.runtime.binding.a2a.internal.discovery.A2aDiscoveryRequest;
import io.aklivity.zilla.runtime.binding.a2a.internal.discovery.A2aDiscoveryResponse;
import io.aklivity.zilla.runtime.binding.a2a.internal.discovery.A2aDiscoverySearch;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex.CompletionCallback;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class A2aDiscoveryFactory
{
    private static final String A2A_TYPE_NAME = "a2a";

    private static final int ERROR_CODE_NOT_FOUND = 404;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final int ERROR_CODE_INTERNAL = -32603;
    private static final String ERROR_MESSAGE_AGENT_NOT_FOUND = "Agent not found";
    private static final String ERROR_MESSAGE_INVALID_PARAMS = "Invalid params";
    private static final String ERROR_MESSAGE_INTERNAL = "Internal error";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final A2aDataExFW.Builder a2aDataExRW = new A2aDataExFW.Builder();
    private final A2aResetExFW.Builder a2aResetExRW = new A2aResetExFW.Builder();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BufferPool decodePool;
    private final LongUnaryOperator supplyReplyId;
    private final long affinity;
    private final int a2aTypeId;
    private final int decodeMax;

    private final Long2ObjectHashMap<A2aDiscoverySearch> searches = new Long2ObjectHashMap<>();

    public A2aDiscoveryFactory(
        A2aConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.decodePool = context.bufferPool();
        this.supplyReplyId = context::supplyReplyId;
        this.affinity = context.affinity();
        this.a2aTypeId = context.supplyTypeId(A2A_TYPE_NAME);
        this.decodeMax = decodePool.slotCapacity();
    }

    public void attach(
        A2aProxyBindingConfig binding,
        A2aAgentCards cards)
    {
        searches.put(binding.id, new A2aDiscoverySearch(context, cards, binding.discovery.search));
    }

    public void detach(
        long bindingId)
    {
        searches.remove(bindingId);
    }

    public MessageConsumer newStream(
        A2aBeginExFW beginEx,
        BeginFW begin,
        MessageConsumer sender)
    {
        final long routedId = begin.routedId();
        final A2aDiscoverySearch search = searches.get(routedId);

        final int contentLength = switch (beginEx.kind())
        {
        case KIND_MESSAGE_SEND -> beginEx.messageSend().contentLength();
        case KIND_MESSAGE_STREAM -> beginEx.messageStream().contentLength();
        default -> 0;
        };

        final A2aDiscoveryStream stream = new A2aDiscoveryStream(
            sender, begin.originId(), routedId, begin.streamId(), beginEx.kind(), contentLength, search);

        return stream::onAppMessage;
    }

    private final class A2aDiscoveryStream
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final int kind;
        private final int contentLength;
        private final A2aDiscoverySearch search;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private DirectBufferEx replyBuffer;
        private int replyLimit;
        private int replyDelivered;
        private boolean replyMessage;

        A2aDiscoveryStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            int kind,
            int contentLength,
            A2aDiscoverySearch search)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.kind = kind;
            this.contentLength = contentLength;
            this.search = search;
        }

        void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onAppBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onAppData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = A2aState.openingInitial(state);
            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();

            switch (kind)
            {
            case KIND_AGENT_CARD:
                initialMax = decodeMax;
                doAppBegin(traceId, authorization);
                doAppWindow(traceId, authorization);
                doReply(traceId, authorization, A2aDiscoveryCard.BYTES, false);
                break;
            case KIND_MESSAGE_SEND:
            case KIND_MESSAGE_STREAM:
                decodeSlot = decodePool.acquire(initialId);

                if (decodeSlot == NO_SLOT)
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_INTERNAL, ERROR_MESSAGE_INTERNAL);
                }
                else
                {
                    initialMax = decodeMax;
                    doAppBegin(traceId, authorization);
                    doAppWindow(traceId, authorization);

                    if (contentLength == 0)
                    {
                        onAppRequest(traceId, authorization);
                    }
                }
                break;
            default:
                doAppResetError(traceId, authorization, ERROR_CODE_NOT_FOUND, ERROR_MESSAGE_AGENT_NOT_FOUND);
                break;
            }
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (decodeSlot != NO_SLOT && payload != null)
            {
                if (decodeSlotOffset + payload.sizeof() > decodeMax)
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
                }
                else
                {
                    final MutableDirectBufferEx slot = decodePool.buffer(decodeSlot);
                    slot.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();

                    initialAck = initialSeq;
                    doAppWindow(traceId, authorization);

                    if (contentLength >= 0 && decodeSlotOffset >= contentLength)
                    {
                        onAppRequest(traceId, authorization);
                    }
                }
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            initialSeq = flush.sequence() + flush.reserved();
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            initialSeq = end.sequence();
            state = A2aState.closeInitial(state);

            if (decodeSlot != NO_SLOT)
            {
                onAppRequest(traceId, authorization);
            }
        }

        private void onAppRequest(
            long traceId,
            long authorization)
        {
            if (decodeSlot != NO_SLOT)
            {
                final byte[] body = new byte[decodeSlotOffset];
                decodePool.buffer(decodeSlot).getBytes(0, body);
                cleanupDecodeSlot();

                final A2aDiscoveryRequest request = A2aDiscoveryRequest.parse(body);

                if (request != null)
                {
                    search.find(authorization, request.query, request.limit, new CompletionCallback<>()
                    {
                        @Override
                        public void completed(
                            List<A2aDiscoveryMatch> matches)
                        {
                            onFound(traceId, authorization, A2aDiscoveryResponse.render(request.messageId, matches));
                        }

                        @Override
                        public void failed(
                            Throwable ex)
                        {
                            onFailed(traceId, authorization);
                        }
                    });
                }
                else
                {
                    doAppResetError(traceId, authorization, ERROR_CODE_INVALID_PARAMS, ERROR_MESSAGE_INVALID_PARAMS);
                }
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            state = A2aState.closeReply(A2aState.closeInitial(state));
            cleanup();
        }

        private void onAppWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyBud = window.budgetId();
            replyPad = window.padding();
            state = A2aState.openReply(state);

            if (replyBuffer != null)
            {
                flushReply(window.traceId(), window.authorization());
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            state = A2aState.closeReply(state);
            cleanup();
        }

        private void onFound(
            long traceId,
            long authorization,
            byte[] response)
        {
            if (!A2aState.replyClosed(state))
            {
                doReply(traceId, authorization, response, true);
            }
        }

        private void onFailed(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                doAppResetError(traceId, authorization, ERROR_CODE_INTERNAL, ERROR_MESSAGE_INTERNAL);
            }
        }

        private void doReply(
            long traceId,
            long authorization,
            byte[] response,
            boolean message)
        {
            replyBuffer = new UnsafeBufferEx(response);
            replyLimit = response.length;
            replyDelivered = 0;
            replyMessage = message;

            if (A2aState.replyOpened(state))
            {
                flushReply(traceId, authorization);
            }
        }

        private void flushReply(
            long traceId,
            long authorization)
        {
            final int length = Math.min(Math.max(replyMax - (int) (replySeq - replyAck) - replyPad, 0),
                replyLimit - replyDelivered);

            if (length > 0)
            {
                final int reserved = length + replyPad;
                final int flags = (replyDelivered == 0 ? INIT : 0) | (replyDelivered + length == replyLimit ? FIN : 0);
                final Flyweight extension = replyDelivered == 0 && replyMessage
                    ? a2aDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(a2aTypeId)
                        .message(m -> m.contextId((String) null))
                        .build()
                    : null;

                doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, replyBud, reserved, replyBuffer, replyDelivered, length, extension);

                replySeq += reserved;
                replyDelivered += length;

                assert replySeq <= replyAck + replyMax;
            }

            if (replyDelivered == replyLimit)
            {
                replyBuffer = null;
                doAppEnd(traceId, authorization);
            }
        }

        private void doAppBegin(
            long traceId,
            long authorization)
        {
            state = A2aState.openingReply(state);

            doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, null);
        }

        private void doAppWindow(
            long traceId,
            long authorization)
        {
            doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, 0);
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!A2aState.replyClosed(state))
            {
                state = A2aState.closeReply(state);
                doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, null);
            }
        }

        private void doAppResetError(
            long traceId,
            long authorization,
            int code,
            String message)
        {
            final A2aResetExFW resetEx = a2aResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(a2aTypeId)
                .error(e -> e.code(code).message(message))
                .build();

            if (!A2aState.replyOpening(state))
            {
                initialMax = decodeMax;
                doAppBegin(traceId, authorization);
                doAppWindow(traceId, authorization);
            }

            cleanupDecodeSlot();
            replyBuffer = null;

            if (!A2aState.initialClosed(state))
            {
                state = A2aState.closeInitial(state);
                doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
            }
        }

        private void cleanup()
        {
            cleanupDecodeSlot();
            replyBuffer = null;
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
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
        long affinity,
        Flyweight extension)
    {
        final BeginFW.Builder builder = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final BeginFW begin = builder.build();

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
        int flags,
        long budgetId,
        int reserved,
        DirectBufferEx payload,
        int offset,
        int length,
        Flyweight extension)
    {
        final DataFW.Builder builder = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(payload, offset, length);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final DataFW data = builder.build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        long authorization,
        Flyweight extension)
    {
        final EndFW.Builder builder = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final EndFW end = builder.build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW.Builder builder = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization);

        if (extension != null)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
        }

        final ResetFW reset = builder.build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
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
        int padding,
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
            .capabilities(capabilities)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
