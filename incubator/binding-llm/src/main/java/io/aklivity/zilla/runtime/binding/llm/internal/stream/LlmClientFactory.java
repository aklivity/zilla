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

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.HttpRequestBody;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect.Kind;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmContentEncoder;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmContentEncoderFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Flyweight;
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
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmNativeFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class LlmClientFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String LLM_TYPE_NAME = "llm";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String METHOD_POST = "POST";
    private static final String SCHEME_HTTP = "http";

    // no per-dialect request path is modeled yet (LlmOptionsConfig / llm.idl carry no such field);
    // this fixed placeholder stands in until that config surface exists
    private static final String PATH_DEFAULT = "/";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmFlushExFW llmFlushExRO = new LlmFlushExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();
    private final LlmFlushExFW.Builder llmFlushExRW = new LlmFlushExFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

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
    private final MutableDirectBufferEx stagingBuffer;

    private final BufferPool decodePool;
    private final int decodeMax;

    private final LlmContentDecoderFactory decoders;
    private final LlmContentEncoderFactory encoders;

    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmClientFactory(
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
        this.stagingBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.decoders = new LlmContentDecoderFactory();
        this.encoders = new LlmContentEncoderFactory();
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public int routedTypeId()
    {
        return httpTypeId;
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

        if (route != null && binding.options != null && binding.options.server != null)
        {
            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::wrap);
            final String sourceName = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;

            final LlmDialect target = binding.dialects.resolve(null, null);
            final LlmDialect source = target != null
                ? (target.name().equals(sourceName) ? target : binding.dialects.dialectNamed(sourceName))
                : null;

            if (target != null && source != null)
            {
                final long originId = begin.originId();
                final long initialId = begin.streamId();
                final long streamAffinity = begin.affinity();
                final boolean sameDialect = target == source;
                final LlmContentEncoder encoder = encoders.create(target.contentType(Kind.REQUEST, null, null));

                newStream = new LlmClient(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    authorization,
                    streamAffinity,
                    route.id,
                    binding.options.server,
                    source,
                    target,
                    sameDialect,
                    encoder)::onAppMessage;
            }
        }

        return newStream;
    }

    private final class LlmClient
    {
        private final MessageConsumer app;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long affinity;
        private final LlmDialect source;
        private final LlmDialect target;
        private final boolean sameDialect;
        private final LlmContentEncoder encoder;
        private final LlmHttpClient delegate;

        private final JsonPipeline requestPipeline;
        private final JsonPipeline responsePipeline;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int bodySlot = NO_SLOT;
        private int bodySlotOffset;

        private LlmClient(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity,
            long resolvedId,
            LlmServerConfig server,
            LlmDialect source,
            LlmDialect target,
            boolean sameDialect,
            LlmContentEncoder encoder)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            this.source = source;
            this.target = target;
            this.sameDialect = sameDialect;
            this.encoder = encoder;
            this.requestPipeline = sameDialect ? null : JsonEx.stream(JsonEx.createParser())
                .transform(source.supplyDecoder(Kind.REQUEST))
                .transform(target.supplyEncoder(Kind.REQUEST))
                .into(JsonEx.createGenerator());
            this.responsePipeline = sameDialect ? null : JsonEx.stream(JsonEx.createParser())
                .transform(target.supplyDecoder(Kind.RESPONSE))
                .transform(source.supplyEncoder(Kind.RESPONSE))
                .into(JsonEx.createGenerator());
            this.delegate = new LlmHttpClient(this, routedId, resolvedId, server);
        }

        private int replyWindow()
        {
            return (int) (replyMax - (replySeq - replyAck));
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
            case FlushFW.TYPE_ID:
                onAppFlush(flushRO.wrap(buffer, index, index + length));
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

            initialSeq = sequence;
            initialAck = acknowledge;
            state = LlmState.openingInitial(state);
            state = LlmState.openedInitial(state);

            delegate.doNetBegin(traceId, authorization);

            doAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            initialSeq = sequence + reserved;

            encodeData(traceId, authorization, payload.buffer(), payload.offset(), payload.limit() - payload.offset());

            initialAck = initialSeq;
            doAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final OctetsFW extension = flush.extension();

            initialSeq = sequence;

            final LlmFlushExFW llmFlushEx = extension.get(llmFlushExRO::wrap);
            String event = null;
            OctetsFW payload = null;
            if (llmFlushEx != null && llmFlushEx.kind() == LlmFlushExFW.KIND_RAW)
            {
                final LlmNativeFlushExFW raw = llmFlushEx.raw();
                event = raw.type() != null ? raw.type().asString() : null;
                payload = raw.payload();
            }

            encodeFlush(traceId, authorization, event, payload);
        }

        private void encodeData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            DirectBufferEx content = buffer;
            int contentOffset = offset;
            int contentLength = length;

            if (!sameDialect && length > 0)
            {
                requestPipeline.reset();
                final JsonPipelineResult result = requestPipeline.transform(
                    buffer, offset, offset + length, true, stagingBuffer, 0, stagingBuffer.capacity());
                content = stagingBuffer;
                contentOffset = 0;
                contentLength = result.produced();
            }

            captureBody(content, contentOffset, contentLength);

            if (encoder != null)
            {
                final int encoded = encoder.encodeData(
                    content, contentOffset, contentLength, copyBuffer, 0, copyBuffer.capacity());
                delegate.doNetData(traceId, authorization, copyBuffer, 0, encoded);
            }
            else
            {
                delegate.doNetData(traceId, authorization, content, contentOffset, contentLength);
            }
        }

        private void encodeFlush(
            long traceId,
            long authorization,
            String event,
            OctetsFW payload)
        {
            if (encoder != null)
            {
                final int idLength = payload != null ? payload.sizeof() : 0;
                final int encoded = payload != null
                    ? encoder.encodeFlush(
                        event, payload.buffer(), payload.offset(), idLength, copyBuffer, 0, copyBuffer.capacity())
                    : encoder.encodeFlush(event, emptyRO.buffer(), 0, 0, copyBuffer, 0, copyBuffer.capacity());
                delegate.doNetData(traceId, authorization, copyBuffer, 0, encoded);
            }
        }

        private void captureBody(
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (bodySlot == NO_SLOT)
                {
                    bodySlot = decodePool.acquire(initialId);
                }

                if (bodySlot != NO_SLOT)
                {
                    final int capturable = Math.min(length, decodeMax - bodySlotOffset);
                    if (capturable > 0)
                    {
                        final MutableDirectBufferEx slotBuffer = decodePool.buffer(bodySlot);
                        slotBuffer.putBytes(bodySlotOffset, buffer, offset, capturable);
                        bodySlotOffset += capturable;
                    }
                }
            }
        }

        private LlmContentDecoder resolveDecoder()
        {
            final HttpRequestBody body = bodySlot != NO_SLOT
                ? new LlmJsonRequestBody(decodePool.buffer(bodySlot), 0, bodySlotOffset)
                : null;

            return decoders.create(target.contentType(Kind.RESPONSE, null, body));
        }

        private void cleanupBodySlot()
        {
            if (bodySlot != NO_SLOT)
            {
                decodePool.release(bodySlot);
                bodySlot = NO_SLOT;
                bodySlotOffset = 0;
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closingInitial(state);
            state = LlmState.closedInitial(state);

            delegate.decoder = resolveDecoder();
            cleanupBodySlot();

            delegate.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupBodySlot();

            delegate.doNetAbort(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();

            replyAck = acknowledge;
            replyMax = maximum;

            delegate.resumeDecode(traceId, authorization);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            delegate.doNetReset(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String dialect)
        {
            state = LlmState.openingReply(state);

            final LlmBeginExFW beginEx = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(dialect)
                .build();

            LlmClientFactory.this.doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, beginEx);

            state = LlmState.openedReply(state);
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

            LlmClientFactory.this.doData(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0, 0L, length, copyBuffer, 0, length, dataEx);

            replySeq += length;
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

            LlmClientFactory.this.doFlush(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0, flushEx);
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmClientFactory.this.doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmClientFactory.this.doAbort(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmClientFactory.this.doReset(app, originId, routedId, initialId, initialSeq, initialAck,
                    initialMax, traceId, authorization, emptyRO);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            LlmClientFactory.this.doWindow(app, originId, routedId, initialId, initialSeq, initialAck,
                decodeMax, traceId, authorization, budgetId, padding);
        }
    }

    private final class LlmHttpClient implements LlmContentDecoderOutput
    {
        private final LlmClient client;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String authority;

        private LlmContentDecoder decoder;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private MessageConsumer net;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private long decodeTraceId;
        private long decodeAuthorization;

        private LlmHttpClient(
            LlmClient client,
            long originId,
            long routedId,
            LlmServerConfig server)
        {
            this.client = client;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authority = server.host + ":" + server.port;
        }

        private int initialWindow()
        {
            return (int) (initialMax - (initialSeq - initialAck));
        }

        private void doNetBegin(
            long traceId,
            long authorization)
        {
            state = LlmState.openingInitial(state);

            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HEADER_SCHEME).value(SCHEME_HTTP))
                .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority))
                .headersItem(h -> h.name(HEADER_PATH).value(PATH_DEFAULT))
                .build();

            net = LlmClientFactory.this.newStream(this::onNetMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, client.affinity, httpBeginEx);

            state = LlmState.openedInitial(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            LlmClientFactory.this.doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0, 0L, length, buffer, offset, length, emptyRO);

            initialSeq += length;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmClientFactory.this.doEnd(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closedInitial(state);
                LlmClientFactory.this.doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closedReply(state);
                LlmClientFactory.this.doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            LlmClientFactory.this.doWindow(net, originId, routedId, replyId, replySeq, replyAck,
                decodeMax - decodeSlotOffset, traceId, authorization, budgetId, padding);
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
            final long authorization = begin.authorization();

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;
            state = LlmState.openingReply(state);
            state = LlmState.openedReply(state);

            client.doAppBegin(traceId, authorization, client.source.name());

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

            replySeq = sequence + reserved;

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

                replyAck = replySeq;
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
            final int window = client.replyWindow();
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
                client.doAppData(traceId, authorization, buffer, offset, forwardable);
            }

            return offset + forwardable;
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (client.sameDialect)
            {
                client.doAppData(decodeTraceId, decodeAuthorization, buffer, offset, length);
            }
            else
            {
                client.responsePipeline.reset();
                final JsonPipelineResult result = client.responsePipeline.transform(
                    (DirectBufferEx) buffer, offset, offset + length, true, stagingBuffer, 0, stagingBuffer.capacity());
                client.doAppData(decodeTraceId, decodeAuthorization, stagingBuffer, 0, result.produced());
            }
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            client.doAppFlush(decodeTraceId, decodeAuthorization, event, buffer, offset, length);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closingReply(state);
            state = LlmState.closedReply(state);

            if (decoder == null)
            {
                client.doAppFlush(traceId, authorization, null, emptyRO.buffer(), 0, 0);
            }

            cleanupDecodeSlot();

            client.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupDecodeSlot();

            client.doAppAbort(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            initialAck = acknowledge;
            initialMax = maximum;
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupDecodeSlot();

            client.doAppReset(traceId, authorization);
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
            client.doAppAbort(traceId, authorization);
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
