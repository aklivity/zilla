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
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmDialectEvent;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmDialectTerminator;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmResponseTransformFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

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

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW.Builder llmDataExRW = new LlmDataExFW.Builder();

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);
    private final DirectBufferEx emptyBufferRO = new UnsafeBufferEx(new byte[0]);

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

    private final BufferPool encodePool;

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
        this.encodePool = context.bufferPool().duplicate();
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

            final LlmDialect target = binding.resolveDialect(JsonEnvelope.NONE);
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
        private final boolean transformEvents;
        private final LlmModelEnvelope envelope;
        private final LlmContentEncoder requestEncoder;
        private final JsonPipeline requestPipeline;
        private final JsonPipeline responsePipeline;
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
        private String pendingRequestEvent;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private String replyPendingEvent;
        private boolean replyValueStarted;
        private boolean replyValueEnding;

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
            this.transformEvents = !sameDialect;
            this.envelope = new LlmModelEnvelope();
            this.requestEncoder = codecs.createEncoder(requestContentType);

            this.requestPipeline = requestEncoder != null ? buildRequestPipeline(source, target, sameDialect, envelope) : null;

            this.responsePipeline = transformEvents ? null : buildResponsePipeline(target, envelope);
            this.responseTerminator = transformEvents ? null : target.terminator(Kind.RESPONSE);

            this.delegate = new LlmHttpClient(this, routedId, resolvedId, server, requestContentType);
        }

        private static JsonPipeline buildRequestPipeline(
            LlmDialect source,
            LlmDialect target,
            boolean sameDialect,
            JsonEnvelope envelope)
        {
            final JsonParserEx parser = JsonEx.createParser();
            final JsonGeneratorEx generator = JsonEx.createGenerator();

            JsonStream stream = JsonEx.stream(parser)
                .envelope(envelope)
                .transform(source.supplySchemaValidator(Kind.REQUEST));

            if (!sameDialect)
            {
                stream = stream
                    .transform(source.supplyDecoder(Kind.REQUEST, envelope))
                    .transform(target.supplyEncoder(Kind.REQUEST, envelope));
            }

            return stream.into(generator);
        }

        private static JsonPipeline buildResponsePipeline(
            LlmDialect target,
            JsonEnvelope envelope)
        {
            final JsonParserEx parser = JsonEx.createParser();
            final JsonGeneratorEx generator = JsonEx.createGenerator();

            return JsonEx.stream(parser)
                .envelope(envelope)
                .transform(target.supplySchemaValidator(Kind.RESPONSE))
                .into(generator);
        }

        private int replyWindow()
        {
            return (int) (replyMax - (replySeq - replyAck));
        }

        private boolean replyAvailable()
        {
            return encodeSlot == NO_SLOT;
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
                if ((flags & FLAG_INIT) != 0)
                {
                    final OctetsFW extension = data.extension();
                    final LlmDataExFW llmDataEx = extension.get(llmDataExRO::tryWrap);
                    pendingRequestEvent = llmDataEx != null && llmDataEx.type() != null
                        ? llmDataEx.type().asString()
                        : null;
                }

                appendDecodeSlot(payload.buffer(), payload.offset(), payload.sizeof(), flags, traceId, authorization);
            }

            final long initialAckMax = initialSeq - decodeSlotOffset;
            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;
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

                while (progress < decodeSlotOffset && delegate.requestAvailable())
                {
                    final boolean first = !requestStarted;
                    final boolean last = (decodeSlotFlags & FLAG_FIN) != 0;

                    JsonPipelineResult result = requestPipeline.transform(decodeBuffer, progress, decodeSlotOffset,
                        last, transformBuffer, 0, transformBuffer.capacity());
                    Status status = result.status();

                    if (status == Status.REJECTED)
                    {
                        requestPipeline.reset();
                        cleanupClient(traceId, authorization);
                        return;
                    }

                    if (first)
                    {
                        final int nameLength = requestEncoder.encodeEventName(pendingRequestEvent, copyBuffer, 0,
                            copyBuffer.capacity());
                        if (nameLength > 0)
                        {
                            delegate.doNetData(traceId, authorization, copyBuffer, 0, nameLength);
                        }
                    }

                    requestStarted = true;

                    boolean forwarded = true;
                    while (status == Status.SUSPENDED && forwarded)
                    {
                        forwardEncodedRequestData(traceId, authorization, result.produced());

                        forwarded = delegate.requestAvailable();
                        if (forwarded)
                        {
                            result = requestPipeline.transform(decodeBuffer, progress, decodeSlotOffset,
                                last, transformBuffer, 0, transformBuffer.capacity());
                            status = result.status();

                            if (status == Status.REJECTED)
                            {
                                requestPipeline.reset();
                                cleanupClient(traceId, authorization);
                                return;
                            }
                        }
                    }

                    if (!forwarded)
                    {
                        break;
                    }

                    forwardEncodedRequestData(traceId, authorization, result.produced());

                    if (status == Status.COMPLETED)
                    {
                        final int flushLength = requestEncoder.encodeFlush(emptyRO.buffer(), 0, 0,
                            copyBuffer, 0, copyBuffer.capacity());
                        if (flushLength > 0)
                        {
                            delegate.doNetData(traceId, authorization, copyBuffer, 0, flushLength);
                        }
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

                    final long initialAckMax = initialSeq - decodeSlotOffset;
                    if (initialAckMax > initialAck)
                    {
                        initialAck = initialAckMax;
                        doAppWindow(traceId, authorization);
                    }
                }

                if (decodeSlotOffset == 0)
                {
                    decodePool.release(decodeSlot);
                    decodeSlot = NO_SLOT;
                }
            }
        }

        private void forwardEncodedRequestData(
            long traceId,
            long authorization,
            int producedLength)
        {
            if (producedLength > 0)
            {
                final int encoded = requestEncoder.encodeData(transformBuffer, 0, producedLength,
                    copyBuffer, 0, copyBuffer.capacity());
                if (encoded > 0)
                {
                    delegate.doNetData(traceId, authorization, copyBuffer, 0, encoded);
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

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                encodeReply(traceId, authorization, slotBuffer, 0, encodeSlotOffset);
            }

            delegate.decodeNet(traceId, authorization);
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
            String event,
            boolean last,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (!replyValueStarted)
            {
                replyPendingEvent = event;
            }
            replyValueEnding = last;

            DirectBuffer encodeBuffer = buffer;
            int encodeOffset = offset;
            int encodeLimit = offset + length;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                slotBuffer.putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;

                encodeBuffer = slotBuffer;
                encodeOffset = 0;
                encodeLimit = encodeSlotOffset;
            }

            encodeReply(traceId, authorization, encodeBuffer, encodeOffset, encodeLimit);
        }

        private void encodeReply(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int replyWin = replyMax - (int) (replySeq - replyAck);
            final int length = maxLength == 0 ? 0 : Math.max(Math.min(replyWin, maxLength), 0);

            if (length > 0 || maxLength == 0)
            {
                final boolean first = !replyValueStarted;
                final boolean fin = replyValueEnding && length == maxLength;
                final int flags = (first ? FLAG_INIT : 0) | (fin ? FLAG_FIN : 0);

                Flyweight dataEx = emptyRO;
                if (first)
                {
                    final LlmDataExFW.Builder dataExBuilder = llmDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(llmTypeId);
                    if (replyPendingEvent != null)
                    {
                        dataExBuilder.type(replyPendingEvent);
                    }
                    dataEx = dataExBuilder.build();
                }

                if (length > 0)
                {
                    copyBuffer.putBytes(0, buffer, offset, length);
                }

                LlmClientFactory.this.doData(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, 0L, length, copyBuffer, 0, length, dataEx);

                replySeq += length;
                replyValueStarted = !fin;
                if (fin)
                {
                    replyValueEnding = false;
                    replyPendingEvent = null;
                }
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(replyId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupClient(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                    slotBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (LlmState.replyClosing(state))
                {
                    doAppEndNow(traceId, authorization);
                }
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            state = LlmState.closingReply(state);

            if (encodeSlot == NO_SLOT)
            {
                doAppEndNow(traceId, authorization);
            }
        }

        private void doAppEndNow(
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
                cleanupEncodeSlot();
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
            cleanupEncodeSlot();
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
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
        private boolean responseStarted;

        private long pendingEndTraceId;
        private long pendingEndAuthorization;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private boolean initialStarted;

        private final MutableDirectBufferEx nativeEventBuffer;
        private int nativeEventLength;
        private String nativeEventName;
        private String pendingResponseEvent;
        private final LlmNativeEventOutput nativeOutput;

        private final JsonPipeline eventPipeline;
        private final LlmDialectEvent decodeEvent;
        private final LlmDialectTerminator encodeTerminator;

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

            if (client.transformEvents)
            {
                final JsonTransform decodeTransform = LlmResponseTransformFactory.supplyDecodeTransform(client.target.name());
                final JsonSink encodeSink = LlmResponseTransformFactory.supplyEncodeSink(client.source.name(), nativeOutput);
                this.eventPipeline = JsonEx.stream(JsonEx.createParser()).transform(decodeTransform).into(encodeSink);
                this.decodeEvent = (LlmDialectEvent) decodeTransform;
                this.encodeTerminator = (LlmDialectTerminator) encodeSink;
            }
            else
            {
                this.eventPipeline = null;
                this.decodeEvent = null;
                this.encodeTerminator = null;
            }
        }

        private int initialWindow()
        {
            return (int) (initialMax - (initialSeq - initialAck));
        }

        private boolean requestAvailable()
        {
            return encodeSlot == NO_SLOT;
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
                .headersItem(h -> h.name(HEADER_PATH).value(client.target.requestPath()))
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
            DirectBufferEx encodeBuffer = buffer;
            int encodeOffset = offset;
            int encodeLimit = offset + length;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                slotBuffer.putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;

                encodeBuffer = slotBuffer;
                encodeOffset = 0;
                encodeLimit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, encodeBuffer, encodeOffset, encodeLimit);
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int) (initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin, maxLength), 0);

            if (length > 0)
            {
                final int flags = initialStarted ? 0 : FLAG_INIT;

                LlmClientFactory.this.doData(net, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, 0L, length, buffer, offset, length, emptyRO);

                initialSeq += length;
                initialStarted = true;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                    slotBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (LlmState.initialClosing(state))
                {
                    doNetEndNow(traceId, authorization);
                }
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            state = LlmState.closingInitial(state);

            if (encodeSlot == NO_SLOT)
            {
                doNetEndNow(traceId, authorization);
            }
        }

        private void doNetEndNow(
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
                cleanupEncodeSlot();
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
            final long replyAckMax = replySeq - decodeSlotOffset;
            if (replyAckMax > replyAck)
            {
                replyAck = replyAckMax;
            }

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

            if (decoder == null)
            {
                cleanupNet(traceId, authorization);
            }
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

            if (window > 0 && client.replyAvailable())
            {
                decodeTraceId = traceId;
                decodeAuthorization = authorization;

                progress = decodeContent(buffer, offset, limit);
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

                if (LlmState.replyClosing(state))
                {
                    doAppEnd(pendingEndTraceId, pendingEndAuthorization);
                }
            }
        }

        private int decodeContent(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            if (streaming)
            {
                progress = decoder.decode(buffer, offset, limit, this);
            }
            else if (client.transformEvents)
            {
                if (LlmState.replyClosing(state))
                {
                    progress = decoder.decode(buffer, offset, limit, this);
                }
            }
            else
            {
                progress = decodeJsonContent(buffer, offset, limit);
            }

            return progress;
        }

        private int decodeJsonContent(
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            final boolean last = LlmState.replyClosing(state);

            while ((progress < limit || last) && client.replyAvailable())
            {
                JsonPipelineResult result = client.responsePipeline.transform(buffer, progress, limit, last,
                    transformBuffer, 0, transformBuffer.capacity());
                Status status = result.status();

                boolean forwarded = true;
                while (status == Status.SUSPENDED && forwarded)
                {
                    responseStarted = true;
                    client.doAppData(decodeTraceId, decodeAuthorization, null, false, transformBuffer, 0,
                        result.produced());

                    forwarded = client.replyAvailable();
                    if (forwarded)
                    {
                        result = client.responsePipeline.transform(buffer, progress, limit, last,
                            transformBuffer, 0, transformBuffer.capacity());
                        status = result.status();
                    }
                }

                if (!forwarded)
                {
                    break;
                }

                if (status == Status.REJECTED)
                {
                    client.responsePipeline.reset();
                    cleanupNet(decodeTraceId, decodeAuthorization);
                    progress = limit;
                    break;
                }

                responseStarted = true;

                final int producedLength = result.produced();
                final boolean complete = status == Status.COMPLETED;
                if (producedLength > 0 || complete)
                {
                    client.doAppData(decodeTraceId, decodeAuthorization, null, complete, transformBuffer, 0,
                        producedLength);
                }

                if (complete)
                {
                    responseStarted = false;
                }

                final int consumed = result.consumed();
                if (consumed == 0)
                {
                    break;
                }
                progress += consumed;
            }

            return progress;
        }

        @Override
        public boolean available()
        {
            return client.replyAvailable();
        }

        @Override
        public void event(
            String event)
        {
            if (event != null)
            {
                if (client.transformEvents)
                {
                    nativeEventName = event;
                }
                else
                {
                    client.envelope.set(ENVELOPE_EVENT, asBuffer(event));
                    pendingResponseEvent = event;
                }
            }
        }

        @Override
        public void data(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (client.transformEvents)
            {
                nativeEventBuffer.putBytes(nativeEventLength, buffer, offset, length);
                nativeEventLength += length;
            }
            else
            {
                forwardResponseContent(pendingResponseEvent, buffer, offset, length);
            }
        }

        @Override
        public void flush(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (client.transformEvents)
            {
                transformNativeEvent();
            }
            else
            {
                pendingResponseEvent = null;
            }
        }

        private void transformNativeEvent()
        {
            if (streaming)
            {
                transformNativeStreamEvent();
            }
            else
            {
                String data = nativeEventBuffer.getStringWithoutLengthUtf8(0, nativeEventLength);
                JsonObject canonical = client.target.decodeMessage(data);
                String encoded = client.source.encodeMessage(canonical);
                byte[] bytes = encoded.getBytes(UTF_8);
                copyBuffer.putBytes(0, bytes);
                onNativeEvent(null, copyBuffer, 0, bytes.length);
            }

            nativeEventName = null;
            nativeEventLength = 0;
        }

        private void transformNativeStreamEvent()
        {
            if (matchesTerminator(nativeEventBuffer, 0, nativeEventLength, client.target.terminator(Kind.RESPONSE)))
            {
                encodeTerminator.terminate();
            }
            else
            {
                decodeEvent.event(nativeEventName);

                Status status = eventPipeline.transform(nativeEventBuffer, 0, nativeEventLength, true);
                while (status == Status.SUSPENDED)
                {
                    status = eventPipeline.transform(nativeEventBuffer, 0, nativeEventLength, true);
                }

                if (status == Status.REJECTED)
                {
                    eventPipeline.reset();
                    cleanupNet(decodeTraceId, decodeAuthorization);
                }
                else
                {
                    eventPipeline.nextDocument();
                }
            }
        }

        private void onNativeEvent(
            String name,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            client.doAppData(decodeTraceId, decodeAuthorization, name, true, buffer, offset, length);
        }

        private boolean matchesTerminator(
            DirectBuffer buffer,
            int offset,
            int length,
            DirectBufferEx terminator)
        {
            boolean matches = terminator != null;
            if (matches)
            {
                comparisonRO.wrap((DirectBufferEx) buffer, offset, length);
                matches = comparisonRO.equals(terminator);
            }
            return matches;
        }

        private void forwardResponseContent(
            String event,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            if (length > 0)
            {
                if (matchesTerminator(buffer, offset, length, client.responseTerminator))
                {
                    client.doAppData(decodeTraceId, decodeAuthorization, event, true, buffer, offset, length);
                }
                else
                {
                    final JsonPipeline pipeline = client.responsePipeline;

                    JsonPipelineResult result = pipeline.transform((DirectBufferEx) buffer, offset, offset + length,
                        true, transformBuffer, 0, transformBuffer.capacity());

                    boolean forwarded = true;
                    while (result.status() == Status.SUSPENDED && forwarded)
                    {
                        if (result.produced() > 0)
                        {
                            client.doAppData(decodeTraceId, decodeAuthorization, event, false, transformBuffer, 0,
                                result.produced());
                        }

                        forwarded = client.replyAvailable();
                        if (forwarded)
                        {
                            result = pipeline.transform((DirectBufferEx) buffer, offset, offset + length,
                                true, transformBuffer, 0, transformBuffer.capacity());
                        }
                    }

                    if (!forwarded)
                    {
                        pipeline.reset();
                        cleanupNet(decodeTraceId, decodeAuthorization);
                    }
                    else if (result.status() == Status.REJECTED)
                    {
                        pipeline.reset();
                        cleanupNet(decodeTraceId, decodeAuthorization);
                    }
                    else
                    {
                        final int producedLength = result.produced();
                        final boolean complete = result.status() == Status.COMPLETED;
                        if (producedLength > 0 || complete)
                        {
                            client.doAppData(decodeTraceId, decodeAuthorization, event, true, transformBuffer, 0,
                                producedLength);
                        }

                        if (result.status() == Status.COMPLETED)
                        {
                            pipeline.nextDocument();
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

            pendingEndTraceId = traceId;
            pendingEndAuthorization = authorization;

            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, authorization, slotBuffer, 0, decodeSlotOffset);
            }
            else if (responseStarted)
            {
                decodeNet(traceId, authorization, emptyBufferRO, 0, 0);
            }

            if (decodeSlot == NO_SLOT)
            {
                doAppEnd(pendingEndTraceId, pendingEndAuthorization);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupDecodeSlot();
            resetDecodeState();

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

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                encodeNet(traceId, authorization, slotBuffer, 0, encodeSlotOffset);
            }

            if (encodeSlot == NO_SLOT)
            {
                client.decodeRequest(traceId, authorization);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupDecodeSlot();
            resetDecodeState();

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

        private void decodeNet(
            long traceId,
            long authorization)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slotBuffer = decodePool.buffer(decodeSlot);
                decodeNet(traceId, authorization, slotBuffer, 0, decodeSlotOffset);

                doNetWindow(traceId, authorization);
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                state = LlmState.closeReply(state);
                client.doAppEnd(traceId, authorization);
            }
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot();
            resetDecodeState();
            cleanupEncodeSlot();
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
        }

        private void resetDecodeState()
        {
            if (client.responsePipeline != null)
            {
                client.responsePipeline.reset();
            }
            if (eventPipeline != null)
            {
                eventPipeline.reset();
            }
            responseStarted = false;
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
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
