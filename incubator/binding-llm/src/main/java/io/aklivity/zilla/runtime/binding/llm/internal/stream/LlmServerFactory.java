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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.HttpHeaders;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class LlmServerFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String LLM_TYPE_NAME = "llm";
    private static final String HEADER_PATH = ":path";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmFlushExFW.Builder llmFlushExRW = new LlmFlushExFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final LlmHttpHeaders httpHeadersRO = new LlmHttpHeaders();

    private final LlmConfiguration config;
    private final EngineContext context;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final long affinity;

    private final int httpTypeId;
    private final int llmTypeId;

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx copyBuffer;

    private final BufferPool decodePool;
    private final int decodeMax;

    private final LlmContentDecoderFactory decoders;

    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmServerFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.affinity = context.affinity();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.llmTypeId = context.supplyTypeId(LLM_TYPE_NAME);
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.decodeMax = decodePool.slotCapacity();
        this.copyBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.decoders = new LlmContentDecoderFactory();
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return llmTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new LlmBindingConfig(binding));
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
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routedId = begin.routedId();
        final long authorization = begin.authorization();

        final LlmBindingConfig binding = bindings.get(routedId);
        final LlmRouteConfig route = binding != null ? binding.resolve(authorization) : null;

        MessageConsumer newStream = null;

        if (route != null)
        {
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::wrap);
            final String path = headerValue(httpBeginEx, HEADER_PATH);
            final LlmDialect dialect = binding.dialects.resolve(path, httpHeadersRO.wrap(httpBeginEx));

            if (dialect != null)
            {
                final long originId = begin.originId();
                final long initialId = begin.streamId();
                final long streamAffinity = begin.affinity();
                final LlmContentDecoder decoder = decoders.create(dialect.contentType());

                newStream = new LlmServer(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    authorization,
                    streamAffinity,
                    route.id,
                    dialect,
                    decoder)::onNetMessage;
            }
        }

        return newStream;
    }

    private static String headerValue(
        HttpBeginExFW httpBeginEx,
        String name)
    {
        final HttpHeaderFW header = httpBeginEx.headers().matchFirst(h -> name.equals(h.name().asString()));
        return header != null ? header.value().asString() : null;
    }

    private final class LlmServer implements LlmContentDecoderOutput
    {
        private final MessageConsumer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;
        private final LlmContentDecoder decoder;
        private final LlmStream stream;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private long decodeTraceId;
        private long decodeAuthorization;

        private LlmServer(
            MessageConsumer net,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity,
            long resolvedId,
            LlmDialect dialect,
            LlmContentDecoder decoder)
        {
            this.net = net;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            this.decoder = decoder;
            this.stream = new LlmStream(this, routedId, resolvedId, dialect.name());
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onNetBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onNetData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onNetEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onNetAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onNetReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();

            initialSeq = sequence;
            initialAck = acknowledge;
            state = LlmState.openingInitial(state);
            state = LlmState.openedInitial(state);

            stream.doAppBegin(traceId, authorization);

            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            initialSeq = sequence + reserved;

            DirectBufferEx buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();

            if (decodeSlot != NO_SLOT && decodeSlotOffset + (limit - offset) > decodeMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                }

                decodeNet(traceId, authorization, buffer, offset, limit);

                initialAck = initialSeq;
                doNetWindow(traceId, authorization, budgetId, 0);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int window = stream.initialWindow();
            int progress = offset;

            if (window > 0)
            {
                decodeTraceId = traceId;
                decodeAuthorization = authorization;

                progress = decoder != null
                    ? decodeContent(buffer, offset, limit, window)
                    : forwardOpaque(traceId, authorization, buffer, offset, limit, window);
            }

            if (progress < limit)
            {
                final int remaining = limit - progress;

                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                    slotBuffer.putBytes(0, buffer, progress, remaining);
                    decodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupDecodeSlot();
            }
        }

        private int decodeContent(
            DirectBufferEx buffer,
            int offset,
            int limit,
            int window)
        {
            final int decodeLimit = offset + Math.min(limit - offset, window);
            return decoder.decode(buffer, offset, decodeLimit, this);
        }

        private int forwardOpaque(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit,
            int window)
        {
            final int forwardable = Math.min(limit - offset, window);

            if (forwardable > 0)
            {
                stream.doAppData(traceId, authorization, buffer, offset, forwardable);
            }

            return offset + forwardable;
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            stream.doAppData(decodeTraceId, decodeAuthorization, buffer, offset, length);
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            stream.doAppFlush(decodeTraceId, decodeAuthorization, event, buffer, offset, length);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closingInitial(state);
            state = LlmState.closedInitial(state);

            if (decoder == null)
            {
                stream.doAppFlush(traceId, authorization, null, emptyRO.buffer(), 0, 0);
            }
            else
            {
                decodeTraceId = traceId;
                decodeAuthorization = authorization;
                decoder.decode(emptyRO.buffer(), 0, 0, this);
            }

            cleanupDecodeSlot();

            stream.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupDecodeSlot();

            stream.doAppAbort(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            replyAck = acknowledge;
            replyMax = maximum;

            stream.doAppWindow(traceId, authorization, budgetId, padding);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupDecodeSlot();

            stream.doAppReset(traceId, authorization);
        }

        private void resumeDecode(
            long traceId,
            long authorization)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, authorization, slotBuffer, 0, decodeSlotOffset);
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot();
            doNetReset(traceId, authorization);
            stream.doAppAbort(traceId, authorization);
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

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            LlmServerFactory.this.doWindow(net, originId, routedId, initialId, initialSeq, initialAck,
                decodeMax - decodeSlotOffset, traceId, authorization, budgetId, padding);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmServerFactory.this.doReset(net, originId, routedId, initialId, initialSeq, initialAck,
                    initialMax, traceId, authorization, emptyRO);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            state = LlmState.openingReply(state);

            LlmServerFactory.this.doBegin(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, extension);

            state = LlmState.openedReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload)
        {
            LlmServerFactory.this.doData(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, flags, budgetId, reserved, payload);

            replySeq += reserved;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmServerFactory.this.doEnd(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmServerFactory.this.doAbort(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }
    }

    private final class LlmStream
    {
        private final LlmServer server;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String dialect;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private MessageConsumer app;

        private LlmStream(
            LlmServer server,
            long originId,
            long routedId,
            String dialect)
        {
            this.server = server;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.dialect = dialect;
        }

        private int initialWindow()
        {
            return (int) (initialMax - (initialSeq - initialAck));
        }

        private void doAppBegin(
            long traceId,
            long authorization)
        {
            state = LlmState.openingInitial(state);

            final LlmBeginExFW beginEx = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(dialect)
                .build();

            app = LlmServerFactory.this.newStream(this::onAppMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, server.affinity, beginEx);

            state = LlmState.openedInitial(state);
        }

        private void doAppData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            copyBuffer.putBytes(0, buffer, offset, length);

            final LlmDataExFW dataEx = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .build();

            LlmServerFactory.this.doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0, 0L, length, copyBuffer, 0, length, dataEx);

            initialSeq += length;
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final LlmFlushExFW flushEx;
            if (length > 0)
            {
                copyBuffer.putBytes(0, buffer, offset, length);
                flushEx = llmFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .raw(r -> r.choiceIndex(0).type(event).payload(copyBuffer, 0, length))
                    .build();
            }
            else
            {
                flushEx = llmFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .typeId(llmTypeId)
                    .raw(r -> r.choiceIndex(0).type(event))
                    .build();
            }

            LlmServerFactory.this.doFlush(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, flushEx);
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmServerFactory.this.doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmServerFactory.this.doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmServerFactory.this.doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            LlmServerFactory.this.doWindow(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onAppBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onAppData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onAppEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAppAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onAppWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAppReset(resetRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;
            state = LlmState.openingReply(state);
            state = LlmState.openedReply(state);

            server.doNetBegin(traceId, authorization, extension);

            doAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            replySeq = sequence + reserved;

            server.doNetData(traceId, authorization, flags, budgetId, reserved, payload);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closedReply(state);

            server.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = LlmState.closedReply(state);

            server.doNetAbort(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();

            initialAck = acknowledge;
            initialMax = maximum;

            server.resumeDecode(traceId, authorization);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = LlmState.closedInitial(state);

            server.doNetReset(traceId, authorization);
        }
    }

    private final class LlmHttpHeaders implements HttpHeaders
    {
        private HttpBeginExFW httpBeginEx;

        private LlmHttpHeaders wrap(
            HttpBeginExFW httpBeginEx)
        {
            this.httpBeginEx = httpBeginEx;
            return this;
        }

        @Override
        public String header(
            String name)
        {
            return headerValue(httpBeginEx, name);
        }
    }

    private MessageConsumer newStream(
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        if (receiver != null)
        {
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        return receiver;
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
        OctetsFW extension)
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
        OctetsFW payload)
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
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
            .payload(payload, offset, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

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
        OctetsFW extension)
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

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
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
        int reserved,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        OctetsFW extension)
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
        int padding)
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
