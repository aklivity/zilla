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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.LongUnaryOperator;

import jakarta.json.JsonObject;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.llm.LlmServerConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect.Kind;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmContentDecoderOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.decode.LlmSseContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmContentEncoder;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapper;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
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
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class LlmClientFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String LLM_TYPE_NAME = "llm";
    private static final String HEADER_METHOD = ":method";
    private static final String HEADER_SCHEME = ":scheme";
    private static final String HEADER_AUTHORITY = ":authority";
    private static final String HEADER_PATH = ":path";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String METHOD_POST = "POST";
    private static final String SCHEME_HTTP = "http";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ENVELOPE_EVENT = "event";

    // no per-dialect request path is modeled yet (LlmOptionsConfig / llm.idl carry no such field);
    // this fixed placeholder stands in until that config surface exists
    private static final String PATH_DEFAULT = "/";

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmFlushExFW llmFlushExRO = new LlmFlushExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmFlushExFW.Builder llmFlushExRW = new LlmFlushExFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final EngineContext context;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;

    private final int httpTypeId;
    private final int llmTypeId;

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final MutableDirectBufferEx copyBuffer;
    private final DirectBufferEx comparisonRO;

    private final BufferPool decodePool;
    private final int decodeMax;

    private final LlmContentCodecFactory codecs;

    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmClientFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.llmTypeId = context.supplyTypeId(LLM_TYPE_NAME);
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.decodeMax = decodePool.slotCapacity();
        this.transformBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.copyBuffer = new UnsafeBufferEx(new byte[decodeMax]);
        this.comparisonRO = new UnsafeBufferEx(new byte[0]);
        this.codecs = new LlmContentCodecFactory();
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
        bindings.put(binding.id, new LlmBindingConfig(binding, context));
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
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);
            final String sourceName = llmBeginEx != null ? llmBeginEx.dialect().asString() : null;
            final String contentType = llmBeginEx != null ? llmBeginEx.contentType().asString() : null;
            final String requestContentType = contentType != null ? contentType : CONTENT_TYPE_JSON;

            final LlmDialect target = binding.resolveDialect(ModelEnvelope.NONE);
            final LlmDialect source = target != null
                ? (target.name().equals(sourceName) ? target : binding.dialectNamed(sourceName))
                : null;

            if (target != null && source != null)
            {
                final long originId = begin.originId();
                final long initialId = begin.streamId();
                final long streamAffinity = begin.affinity();

                newStream = new LlmClient(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    authorization,
                    streamAffinity,
                    route.id,
                    binding.options.server,
                    binding,
                    source,
                    target,
                    requestContentType)::onAppMessage;
            }
        }

        return newStream;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String authorizationCredentials(
        LlmBindingConfig binding,
        long authorization)
    {
        String header = null;

        if (binding.guard != null && binding.credentials != null && (authorization & GuardHandler.MASK_AUTHORIZED) != 0L)
        {
            final String credentials = binding.guard.credentials(authorization);
            header = credentials != null
                ? binding.credentials.replace(LlmBindingConfig.CREDENTIALS_PLACEHOLDER, credentials)
                : null;
        }

        return header;
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
        private final LlmBindingConfig binding;
        private final LlmDialect source;
        private final LlmDialect target;
        private final boolean sameDialect;
        private final LlmEventMapper targetEventMapper;
        private final LlmEventMapper sourceEventMapper;
        private final boolean translateEvents;
        private final LlmModelEnvelope envelope;
        private final LlmContentEncoder requestEncoder;
        private final ModelPipeline requestPipeline;
        private final ModelPipeline responsePipeline;
        private final DirectBufferEx responseTerminator;
        private final LlmHttpClient delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotFlags;
        private boolean requestStarted;

        private final MutableDirectBufferEx pendingContent;
        private int pendingContentLength;

        private LlmClient(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long affinity,
            long resolvedId,
            LlmServerConfig server,
            LlmBindingConfig binding,
            LlmDialect source,
            LlmDialect target,
            String requestContentType)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.affinity = affinity;
            this.binding = binding;
            this.source = source;
            this.target = target;
            this.sameDialect = source == target;
            this.targetEventMapper = sameDialect ? null : LlmEventMapperFactory.supply(target.name(), llmTypeId);
            this.sourceEventMapper = sameDialect ? null : LlmEventMapperFactory.supply(source.name(), llmTypeId);
            this.envelope = new LlmModelEnvelope();
            this.requestEncoder = codecs.createEncoder(requestContentType);
            this.pendingContent = new UnsafeBufferEx(new byte[copyBuffer.capacity()]);

            this.translateEvents = targetEventMapper != null && sourceEventMapper != null;

            final ModelTransform requestTransform = sameDialect
                ? ModelTransform.NONE
                : source.supplyDecoder(Kind.REQUEST, envelope).andThen(target.supplyEncoder(Kind.REQUEST, envelope));
            this.requestPipeline = requestEncoder != null
                ? binding.supplyModel(source, Kind.REQUEST).supplyDecoder(envelope, requestTransform, ModelCache.NONE)
                : null;

            final ModelTransform responseTransform = sameDialect
                ? ModelTransform.NONE
                : target.supplyDecoder(Kind.RESPONSE, envelope).andThen(source.supplyEncoder(Kind.RESPONSE, envelope));
            this.responsePipeline = translateEvents
                ? null
                : binding.supplyModel(target, Kind.RESPONSE).supplyDecoder(envelope, responseTransform, ModelCache.NONE);
            this.responseTerminator = translateEvents ? null : target.terminator(Kind.RESPONSE);

            this.delegate = new LlmHttpClient(this, routedId, resolvedId, server, requestContentType);
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
            case ChallengeFW.TYPE_ID:
                onAppChallenge(challengeRO.wrap(buffer, index, index + length));
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
            state = LlmState.openInitial(state);

            // deferred until delegate.onNetWindow() reports the network transport is
            // ready, rather than granted unconditionally here -- granting it before the
            // transport can accept a request risks a request arriving too early and
            // being rejected as a window violation
            delegate.doNetBegin(traceId, authorization);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (requestEncoder == null)
            {
                delegate.doNetData(traceId, authorization, payload.buffer(), payload.offset(), payload.sizeof());
            }
            else
            {
                appendDecodeSlot(payload.buffer(), payload.offset(), payload.sizeof(), flags, traceId, authorization);
            }

            doAppWindow(traceId, authorization);
        }

        private void appendDecodeSlot(
            DirectBufferEx buffer,
            int offset,
            int length,
            int flags,
            long traceId,
            long authorization)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT)
            {
                cleanupClient(traceId, authorization);
            }
            else
            {
                final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                decodeBuffer.putBytes(decodeSlotOffset, buffer, offset, length);
                decodeSlotOffset += length;
                decodeSlotFlags = flags;

                decodeRequest(traceId, authorization);
            }
        }

        private void decodeRequest(
            long traceId,
            long authorization)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                int progress = 0;

                while (progress < decodeSlotOffset)
                {
                    final boolean first = !requestStarted;
                    final int callFlags = (first ? decodeSlotFlags & FLAG_INIT : 0) | (decodeSlotFlags & FLAG_FIN);

                    final ModelPipelineResult result = requestPipeline.transform(traceId, routedId, authorization,
                        callFlags, decodeBuffer, progress, decodeSlotOffset, transformBuffer, 0, transformBuffer.capacity());
                    final ModelStatus status = result.status();

                    if (status == ModelStatus.REJECTED)
                    {
                        requestPipeline.reset();
                        cleanupClient(traceId, authorization);
                        return;
                    }

                    requestStarted = true;

                    final int producedLength = result.produced();
                    if (producedLength > 0)
                    {
                        forwardRequestContent(traceId, authorization, transformBuffer, producedLength);
                    }

                    if (status == ModelStatus.COMPLETE)
                    {
                        requestPipeline.reset();
                        requestStarted = false;
                    }

                    final int consumed = result.consumed();
                    if (consumed == 0)
                    {
                        break;
                    }
                    progress += consumed;
                }

                if (progress > 0)
                {
                    decodeBuffer.putBytes(0, decodeBuffer, progress, decodeSlotOffset - progress);
                    decodeSlotOffset -= progress;
                }

                if (decodeSlotOffset == 0)
                {
                    decodePool.release(decodeSlot);
                    decodeSlot = NO_SLOT;
                }
            }
        }

        private void forwardRequestContent(
            long traceId,
            long authorization,
            MutableDirectBufferEx buffer,
            int length)
        {
            final int available = pendingContent.capacity() - pendingContentLength;
            final int appended = Math.min(length, available);

            if (appended > 0)
            {
                pendingContent.putBytes(pendingContentLength, buffer, 0, appended);
                pendingContentLength += appended;
            }
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final OctetsFW extension = flush.extension();

            initialSeq = flush.sequence();

            if (requestEncoder != null)
            {
                final LlmFlushExFW llmFlushEx = extension.get(llmFlushExRO::tryWrap);
                if (llmFlushEx != null && llmFlushEx.kind() == LlmFlushExFW.KIND_RAW)
                {
                    final LlmNativeFlushExFW raw = llmFlushEx.raw();
                    final String event = raw.type() != null ? raw.type().asString() : null;
                    final OctetsFW payload = raw.payload();
                    final int idLength = payload != null ? payload.sizeof() : 0;

                    int position = 0;
                    position += requestEncoder.encodeEventName(event, copyBuffer, position, copyBuffer.capacity());
                    if (pendingContentLength > 0)
                    {
                        position += requestEncoder.encodeData(pendingContent, 0, pendingContentLength,
                            copyBuffer, position, copyBuffer.capacity());
                    }
                    position += payload != null
                        ? requestEncoder.encodeFlush(
                            payload.buffer(), payload.offset(), idLength, copyBuffer, position, copyBuffer.capacity())
                        : requestEncoder.encodeFlush(
                            emptyRO.buffer(), 0, 0, copyBuffer, position, copyBuffer.capacity());
                    pendingContentLength = 0;

                    delegate.doNetData(traceId, authorization, copyBuffer, 0, position);
                }
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closingInitial(state);
            state = LlmState.closeInitial(state);

            // Not every dialect's request forwarding is followed by an app-level FLUSH before END
            // (e.g. a plain, non-streaming JSON request body never advises one) -- anything still held
            // in pendingContent at this point would otherwise be silently dropped instead of forwarded.
            if (pendingContentLength > 0)
            {
                final int encoded = requestEncoder.encodeData(pendingContent, 0, pendingContentLength,
                    copyBuffer, 0, copyBuffer.capacity());
                pendingContentLength = 0;
                if (encoded > 0)
                {
                    delegate.doNetData(traceId, authorization, copyBuffer, 0, encoded);
                }
            }

            delegate.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupDecodeSlot();
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
            state = LlmState.openReply(state);

            delegate.resumeDecode(traceId, authorization);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            cleanupDecodeSlot();
            delegate.doNetReset(traceId);
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            delegate.doNetChallenge(traceId, authorization, extension);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String dialectName,
            String contentType)
        {
            state = LlmState.openingReply(state);

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(dialectName);

            if (contentType != null)
            {
                builder.contentType(contentType);
            }

            final LlmBeginExFW beginEx = builder.build();

            LlmClientFactory.this.doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, affinity, beginEx);

            state = LlmState.openReply(state);
        }

        private void doAppData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            copyBuffer.putBytes(0, buffer, offset, length);

            LlmClientFactory.this.doData(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, FLAG_INIT | FLAG_FIN, 0L, length, copyBuffer, 0, length, emptyRO);

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
                state = LlmState.closeReply(state);
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
                state = LlmState.closeReply(state);
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
                state = LlmState.closeInitial(state);
                LlmClientFactory.this.doReset(app, originId, routedId, initialId, initialSeq, initialAck,
                    initialMax, traceId, authorization, emptyRO);
            }
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            LlmClientFactory.this.doChallenge(app, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, authorization, extension);
        }

        private void doAppWindow(
            long traceId,
            long authorization)
        {
            LlmClientFactory.this.doWindow(app, originId, routedId, initialId, initialSeq, initialAck,
                decodeMax, traceId, authorization, 0L, 0);
        }

        private void cleanupClient(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot();
            doAppReset(traceId, authorization);
            delegate.doNetAbort(traceId, authorization);
        }

        private void cleanupDecodeSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
                requestStarted = false;
            }
            if (requestPipeline != null)
            {
                requestPipeline.reset();
            }
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
        private final String requestContentType;

        private LlmContentDecoder decoder;
        private boolean streaming;

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

        private final MutableDirectBufferEx nativeEventBuffer;
        private int nativeEventLength;
        private String nativeEventName;
        private final LlmEventMapperOutput canonicalOutput;
        private final LlmNativeEventOutput nativeOutput;

        private LlmHttpClient(
            LlmClient client,
            long originId,
            long routedId,
            LlmServerConfig server,
            String requestContentType)
        {
            this.client = client;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authority = server.host + ":" + server.port;
            this.requestContentType = requestContentType;
            this.nativeEventBuffer = new UnsafeBufferEx(new byte[decodeMax]);
            this.nativeOutput = this::onNativeEvent;
            this.canonicalOutput = new LlmEventMapperOutput()
            {
                @Override
                public void data(
                    DirectBuffer buffer,
                    int offset,
                    int length,
                    LlmDataExFW dataEx)
                {
                    client.sourceEventMapper.encode(buffer, offset, length, dataEx, nativeOutput);
                }

                @Override
                public void flush(
                    LlmFlushExFW flushEx)
                {
                    client.sourceEventMapper.encode(flushEx, nativeOutput);
                }

                @Override
                public void end()
                {
                    client.sourceEventMapper.encodeEnd(nativeOutput);
                }
            };
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

            final String credentials = authorizationCredentials(client.binding, authorization);

            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_METHOD).value(METHOD_POST))
                .headersItem(h -> h.name(HEADER_SCHEME).value(SCHEME_HTTP))
                .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority))
                .headersItem(h -> h.name(HEADER_PATH).value(PATH_DEFAULT))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(requestContentType));

            if (credentials != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(client.target.credentialsHeader()).value(credentials));
            }

            final HttpBeginExFW httpBeginEx = httpBeginExBuilder.build();

            net = LlmClientFactory.this.newStream(this::onNetMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, client.affinity, httpBeginEx);

            state = LlmState.openInitial(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            LlmClientFactory.this.doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, FLAG_INIT | FLAG_FIN, 0L, length, buffer, offset, length, emptyRO);

            initialSeq += length;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                state = LlmState.closeInitial(state);
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
                state = LlmState.closeInitial(state);
                LlmClientFactory.this.doAbort(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, emptyRO);
            }
        }

        private void doNetReset(
            long traceId)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closeReply(state);
                LlmClientFactory.this.doReset(net, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, 0L, emptyRO);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            LlmClientFactory.this.doChallenge(net, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, authorization, extension);
        }

        private void doNetWindow(
            long traceId,
            long authorization)
        {
            LlmClientFactory.this.doWindow(net, originId, routedId, replyId, replySeq, replyAck,
                decodeMax - decodeSlotOffset, traceId, authorization, 0L, 0);
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
            case ChallengeFW.TYPE_ID:
                onNetChallenge(challengeRO.wrap(buffer, index, index + length));
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
            state = LlmState.openReply(state);

            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
            String responseContentType = null;
            if (httpBeginEx != null)
            {
                responseContentType = header(httpBeginEx, HEADER_CONTENT_TYPE);
            }

            this.decoder = codecs.createDecoder(responseContentType);
            this.streaming = decoder instanceof LlmSseContentDecoder;

            client.doAppBegin(traceId, authorization, client.source.name(), responseContentType);

            doNetWindow(traceId, authorization);
        }

        private String header(
            HttpBeginExFW httpBeginEx,
            String name)
        {
            String[] value = new String[1];
            httpBeginEx.headers().forEach(h ->
            {
                if (name.equals(h.name().asString()))
                {
                    value[0] = h.value().asString();
                }
            });
            return value[0];
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + data.reserved();

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

                doNetWindow(traceId, authorization);
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
        public void event(
            String event)
        {
            if (event != null)
            {
                if (client.translateEvents)
                {
                    nativeEventName = event;
                }
                else
                {
                    client.envelope.set(ENVELOPE_EVENT, asBuffer(event));
                }
            }
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (client.translateEvents)
            {
                nativeEventBuffer.putBytes(nativeEventLength, buffer, offset, length);
                nativeEventLength += length;
            }
            else
            {
                forwardResponseContent(buffer, offset, length);
            }
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (client.translateEvents)
            {
                translateNativeEvent();
            }
            else
            {
                client.doAppFlush(decodeTraceId, decodeAuthorization, event, buffer, offset, length);
            }
        }

        private void translateNativeEvent()
        {
            String data = nativeEventBuffer.getStringWithoutLengthUtf8(0, nativeEventLength);

            if (streaming)
            {
                client.targetEventMapper.decode(nativeEventName, data, canonicalOutput);
            }
            else
            {
                JsonObject canonical = client.targetEventMapper.decodeMessage(data);
                onNativeEvent(null, client.sourceEventMapper.encodeMessage(canonical));
            }

            nativeEventName = null;
            nativeEventLength = 0;
        }

        private void onNativeEvent(
            String name,
            String data)
        {
            if (data != null && !data.isEmpty())
            {
                byte[] bytes = data.getBytes(UTF_8);
                copyBuffer.putBytes(0, bytes);
                client.doAppData(decodeTraceId, decodeAuthorization, copyBuffer, 0, bytes.length);
            }

            client.doAppFlush(decodeTraceId, decodeAuthorization, name, emptyRO.buffer(), 0, 0);
        }

        private boolean matchesTerminator(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final DirectBufferEx terminator = client.responseTerminator;
            boolean matches = terminator != null;
            if (matches)
            {
                comparisonRO.wrap((DirectBufferEx) buffer, offset, length);
                matches = comparisonRO.equals(terminator);
            }
            return matches;
        }

        private void forwardResponseContent(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (matchesTerminator(buffer, offset, length))
                {
                    client.doAppData(decodeTraceId, decodeAuthorization, (DirectBufferEx) buffer, offset, length);
                }
                else
                {
                    final ModelPipeline pipeline = client.responsePipeline;
                    final int flags = FLAG_INIT | FLAG_FIN;

                    final ModelPipelineResult result = pipeline.transform(decodeTraceId, routedId, decodeAuthorization,
                        flags, (DirectBufferEx) buffer, offset, offset + length, transformBuffer, 0,
                        transformBuffer.capacity());

                    if (result.status() == ModelStatus.REJECTED)
                    {
                        pipeline.reset();
                        cleanupNet(decodeTraceId, decodeAuthorization);
                    }
                    else
                    {
                        final int producedLength = result.produced();
                        if (producedLength > 0)
                        {
                            client.doAppData(decodeTraceId, decodeAuthorization, transformBuffer, 0, producedLength);
                        }

                        if (result.status() == ModelStatus.COMPLETE)
                        {
                            pipeline.reset();
                        }
                    }
                }
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = LlmState.closingReply(state);
            state = LlmState.closeReply(state);

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
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            final boolean transportReady = initialMax <= 0 && maximum > 0;

            initialAck = acknowledge;
            initialMax = maximum;

            if (transportReady)
            {
                client.doAppWindow(traceId, authorization);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupDecodeSlot();

            client.doAppReset(traceId, authorization);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            client.doAppChallenge(traceId, authorization, extension);
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
            doNetReset(traceId);
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
            if (client.responsePipeline != null)
            {
                client.responsePipeline.reset();
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

    private void doChallenge(
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

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
