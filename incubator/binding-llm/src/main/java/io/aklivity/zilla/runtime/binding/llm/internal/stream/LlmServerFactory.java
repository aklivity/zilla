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
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmAuthorizationResult;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.encode.LlmContentEncoder;
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
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class LlmServerFactory implements LlmStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String HEADER_STATUS = ":status";
    private static final String HEADER_CONTENT_TYPE = "content-type";
    private static final String STATUS_OK = "200";
    private static final String STATUS_UNAUTHORIZED = "401";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ENVELOPE_MODEL = "model";

    private static final int FLAG_FIN = 0x01;
    private static final int FLAG_INIT = 0x02;

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();
    private final LlmBeginExFW.Builder llmBeginExRW = new LlmBeginExFW.Builder();
    private final LlmDataExFW llmDataExRO = new LlmDataExFW();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final MutableDirectBufferEx transformBuffer;
    private final MutableDirectBufferEx copyBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final LlmContentCodecFactory codecs;
    private final int llmTypeId;
    private final int httpTypeId;
    private final EngineContext context;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmServerFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.extBuffer = new UnsafeBufferEx(new byte[writeBuffer.capacity()]);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.transformBuffer = new UnsafeBufferEx(new byte[decodePool.slotCapacity()]);
        this.copyBuffer = new UnsafeBufferEx(new byte[encodePool.slotCapacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.codecs = new LlmContentCodecFactory();
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routedId = begin.routedId();
        final long authorization = begin.authorization();

        final LlmBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final LlmRouteConfig route = binding.resolve(authorization);

            if (route != null)
            {
                final LlmModelEnvelope envelope = new LlmModelEnvelope();
                final HttpBeginExFW httpBeginEx = extractHeaders(begin, envelope);

                if (httpBeginEx != null)
                {
                    final LlmDialect dialect = binding.resolveDialect(envelope);

                    if (dialect != null)
                    {
                        final LlmAuthorizationResult authResult = binding.authorize(
                            begin.traceId(), routedId, begin.streamId(), authorization, envelope, dialect);

                        if (!authResult.authorized())
                        {
                            newStream = new LlmUnauthorizedResponder(
                                network, begin.originId(), routedId, begin.streamId(), dialect)::onNetMessage;
                        }
                        else
                        {
                            final String contentType = header(envelope, HEADER_CONTENT_TYPE);
                            final JsonPipeline pipeline = buildRequestPipeline(dialect, envelope);

                            newStream = new LlmServer(
                                    network,
                                    begin.originId(),
                                    begin.routedId(),
                                    begin.streamId(),
                                    route.id,
                                    authResult.authorization(),
                                    dialect,
                                    contentType,
                                    envelope,
                                    pipeline,
                                    authResult.deauthorize())::onNetMessage;
                        }
                    }
                }
            }
        }

        return newStream;
    }

    private JsonPipeline buildRequestPipeline(
        LlmDialect dialect,
        LlmModelEnvelope envelope)
    {
        final JsonParserEx parser = JsonEx.createParser();
        final JsonGeneratorEx generator = JsonEx.createGenerator();

        return JsonEx.stream(parser)
            .envelope(envelope)
            .transform(dialect.supplySchemaValidator(LlmDialect.Kind.REQUEST))
            .transform(dialect.supplyValidator(LlmDialect.Kind.REQUEST, envelope))
            .into(generator);
    }

    private HttpBeginExFW extractHeaders(
        BeginFW begin,
        LlmModelEnvelope envelope)
    {
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        if (httpBeginEx != null)
        {
            httpBeginEx.headers().forEach(h -> envelope.set(h.name().asString(), asBuffer(h.value().asString())));
        }

        return httpBeginEx;
    }

    private static DirectBufferEx asBuffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String header(
        LlmModelEnvelope envelope,
        String name)
    {
        final DirectBufferEx value = envelope.get(name, 0);
        return value != null ? value.getStringWithoutLengthUtf8(0, value.capacity()) : null;
    }

    private static int sliceFlags(
        int flags,
        boolean first,
        boolean last)
    {
        int sliceFlags = 0;
        if (first)
        {
            sliceFlags |= flags & FLAG_INIT;
        }
        if (last)
        {
            sliceFlags |= flags & FLAG_FIN;
        }
        return sliceFlags;
    }

    private static final class SlotChunk
    {
        private final int length;
        private final int flags;
        private final long budgetId;
        private final long traceId;
        private final long authorization;
        private int sent;

        private SlotChunk(
            int length,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            this.length = length;
            this.flags = flags;
            this.budgetId = budgetId;
            this.traceId = traceId;
            this.authorization = authorization;
        }
    }

    private final class LlmServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long exitId;
        private final LlmDialect dialect;
        private final String contentType;
        private final LlmModelEnvelope envelope;
        private final JsonPipeline pipeline;
        private final Runnable deauthorize;

        private LlmStream stream;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;
        private boolean requestStarted;

        private final long initialAuthorization;
        private long pendingEndTraceId;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotFlags;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private final Deque<SlotChunk> encodeChunks = new ArrayDeque<>();
        private boolean flushingReply;

        private long pendingReplyEndTraceId;
        private long pendingReplyEndAuthorization;

        private boolean deauthorized;

        private LlmServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId,
            long authorization,
            LlmDialect dialect,
            String contentType,
            LlmModelEnvelope envelope,
            JsonPipeline pipeline,
            Runnable deauthorize)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.exitId = exitId;
            this.initialAuthorization = authorization;
            this.dialect = dialect;
            this.contentType = contentType;
            this.envelope = envelope;
            this.pipeline = pipeline;
            this.deauthorize = deauthorize;
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
            final long traceId = begin.traceId();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = transformBuffer.capacity();
            state = LlmState.openingInitial(state);

            doNetWindow(traceId);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            initialSeq = data.sequence() + data.reserved();

            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot == NO_SLOT)
            {
                doNetReset(traceId);
                if (stream != null)
                {
                    stream.doAppAbort(traceId);
                }
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                decodeBuffer.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                decodeSlotOffset += payload.sizeof();
                decodeSlotFlags = flags;

                decodeNetwork(traceId);
            }
        }

        private void decodeNetwork(
            long traceId)
        {
            final long authorization = initialAuthorization;

            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                int progress = 0;

                while (progress < decodeSlotOffset && (stream == null || stream.requestAvailable()))
                {
                    final boolean last = (decodeSlotFlags & FLAG_FIN) != 0;

                    JsonPipelineResult result = pipeline.transform(decodeBuffer, progress, decodeSlotOffset, last,
                        transformBuffer, 0, transformBuffer.capacity());
                    Status status = result.status();

                    if (status == Status.REJECTED)
                    {
                        pipeline.reset();
                        doNetReset(traceId);
                        if (stream != null)
                        {
                            stream.doAppAbort(traceId);
                        }
                        cleanup(traceId);
                        return;
                    }

                    if (stream == null)
                    {
                        stream = new LlmStream(this);
                        stream.doAppBegin(traceId, authorization);
                    }

                    boolean forwarded = true;
                    while (status == Status.SUSPENDED && forwarded)
                    {
                        forwardDecodedRequest(result.produced(), false, traceId, authorization);

                        forwarded = stream.requestAvailable();
                        if (forwarded)
                        {
                            result = pipeline.transform(decodeBuffer, progress, decodeSlotOffset, last,
                                transformBuffer, 0, transformBuffer.capacity());
                            status = result.status();

                            if (status == Status.REJECTED)
                            {
                                pipeline.reset();
                                doNetReset(traceId);
                                stream.doAppAbort(traceId);
                                cleanup(traceId);
                                return;
                            }
                        }
                    }

                    if (!forwarded)
                    {
                        break;
                    }

                    forwardDecodedRequest(result.produced(), status == Status.COMPLETED, traceId, authorization);

                    if (status == Status.COMPLETED)
                    {
                        pipeline.reset();
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

            if (stream == null || stream.requestAvailable())
            {
                final long initialAckMax = initialSeq - decodeSlotOffset;
                if (initialAckMax > initialAck)
                {
                    initialAck = initialAckMax;
                    doNetWindow(traceId);
                }

                if (LlmState.initialEndDeferred(state) && decodeSlotOffset == 0)
                {
                    state = LlmState.clearInitialEndDeferred(state);
                    if (stream != null)
                    {
                        stream.doAppEnd(traceId);
                    }
                    else
                    {
                        cleanup(traceId);
                    }
                }
            }
        }

        private void forwardDecodedRequest(
            int producedLength,
            boolean complete,
            long traceId,
            long authorization)
        {
            if (producedLength > 0 || complete)
            {
                final int outputFlags = (requestStarted ? 0 : FLAG_INIT) | (complete ? FLAG_FIN : 0);
                stream.doAppData(transformBuffer, producedLength, outputFlags, traceId, authorization);
                requestStarted = true;
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            initialSeq = end.sequence();
            state = LlmState.closeInitial(state);

            if (decodeSlotOffset == 0 && (stream == null || stream.requestAvailable()))
            {
                if (stream != null)
                {
                    stream.doAppEnd(traceId);
                }
                else
                {
                    cleanup(traceId);
                }
            }
            else
            {
                state = LlmState.deferInitialEnd(state);
                pendingEndTraceId = traceId;
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            initialSeq = abort.sequence();
            state = LlmState.closeInitial(state);

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
            cleanup(traceId);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();

            replyAck = acknowledge;
            replyMax = maximum;
            state = LlmState.openReply(state);

            flushEncodeSlot(traceId);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            if (stream != null)
            {
                stream.doAppAbort(traceId);
            }
            cleanup(traceId);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (stream != null)
            {
                stream.doAppChallenge(traceId, authorization, extension);
            }
        }

        private void doNetData(
            OctetsFW payload,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            doNetData(payload.buffer(), payload.offset(), payload.sizeof(), flags, budgetId, traceId, authorization);
        }

        private void doNetData(
            DirectBufferEx buffer,
            int offset,
            int length,
            int flags,
            long budgetId,
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(replyId);
            }

            if (encodeSlot == NO_SLOT || encodeSlotOffset + length > encodePool.slotCapacity())
            {
                doNetReset(traceId);
                stream.doAppAbort(traceId);
                cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx slotBuffer = encodePool.buffer(encodeSlot);
                slotBuffer.putBytes(encodeSlotOffset, buffer, offset, length);
                encodeSlotOffset += length;
                encodeChunks.add(new SlotChunk(length, flags, budgetId, traceId, authorization));

                flushEncodeSlot(traceId);
            }
        }

        private void flushEncodeSlot(
            long traceId)
        {
            if (flushingReply || encodeSlot == NO_SLOT)
            {
                return;
            }

            flushingReply = true;
            try
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                while (!encodeChunks.isEmpty())
                {
                    final long available = replyMax - (replySeq - replyAck);
                    if (available <= 0)
                    {
                        break;
                    }

                    final SlotChunk chunk = encodeChunks.peek();
                    final int remaining = chunk.length - chunk.sent;
                    final int sliceLength = (int) Math.min(remaining, available);
                    if (sliceLength <= 0)
                    {
                        break;
                    }

                    final boolean first = chunk.sent == 0;
                    final boolean last = chunk.sent + sliceLength == chunk.length;
                    final int outputFlags = sliceFlags(chunk.flags, first, last);

                    final int sliceOffset = chunk.sent;
                    chunk.sent += sliceLength;
                    doNetData(chunk.traceId, chunk.authorization, outputFlags, chunk.budgetId,
                        sliceLength, buffer, sliceOffset, sliceLength);

                    if (chunk.sent >= chunk.length)
                    {
                        encodeChunks.poll();
                        if (encodeSlotOffset > chunk.length)
                        {
                            buffer.putBytes(0, buffer, chunk.length, encodeSlotOffset - chunk.length);
                        }
                        encodeSlotOffset -= chunk.length;
                    }
                }

                if (encodeChunks.isEmpty())
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotOffset = 0;
                }
            }
            finally
            {
                flushingReply = false;
            }

            if (stream != null)
            {
                final long replyAckMax = stream.replySeq - encodeSlotOffset;
                if (replyAckMax > stream.replyAck)
                {
                    stream.replyAck = replyAckMax;
                    stream.doAppWindow(traceId);
                }
            }

            if (LlmState.replyEndDeferred(state) && encodeSlot == NO_SLOT)
            {
                state = LlmState.clearReplyEndDeferred(state);
                doNetEnd(pendingReplyEndTraceId, pendingReplyEndAuthorization, EMPTY_OCTETS);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String responseContentType)
        {
            final HttpBeginExFW.Builder httpBeginExBuilder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(STATUS_OK));

            if (responseContentType != null)
            {
                httpBeginExBuilder.headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(responseContentType));
            }

            final HttpBeginExFW httpBeginEx = httpBeginExBuilder.build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            state = LlmState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            MutableDirectBufferEx buffer,
            int offset,
            int length)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(buffer, offset, length)
                .extension(EMPTY_OCTETS)
                .build();

            network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
            replySeq += reserved;
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

                network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeReply(state);

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

                network.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeReply(state);

                if (!deauthorized)
                {
                    deauthorized = true;
                    deauthorize.run();
                }
            }
        }

        private void doNetWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetReset(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                network.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

            network.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void cleanup(
            long traceId)
        {
            pipeline.reset();
            envelope.clear();
            cleanupDecodeSlot();
            cleanupEncodeSlot();
            if (!deauthorized)
            {
                deauthorized = true;
                deauthorize.run();
            }
            if (stream != null)
            {
                stream.cleanup();
            }
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

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeChunks.clear();
            }
        }
    }

    private final class LlmStream
    {
        private final LlmServer server;

        private MessageConsumer app;
        private long initialId;
        private long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private int encodeSlotSent;
        private int encodeSlotFlags;
        private long encodeSlotTraceId;
        private long encodeSlotAuthorization;
        private boolean flushingRequest;

        private LlmContentEncoder encoder;
        private String pendingResponseEvent;

        private LlmStream(
            LlmServer server)
        {
            this.server = server;
        }

        private boolean requestAvailable()
        {
            return encodeSlot == NO_SLOT;
        }

        private long initialAvailable()
        {
            return initialMax - (initialSeq - initialAck);
        }

        private void doAppData(
            MutableDirectBufferEx source,
            int length,
            int flags,
            long traceId,
            long authorization)
        {
            if (encodeSlot == NO_SLOT)
            {
                encodeSlot = encodePool.acquire(initialId);
            }

            if (encodeSlot == NO_SLOT)
            {
                server.doNetReset(traceId);
                doAppAbort(traceId);
                server.cleanup(traceId);
            }
            else
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                buffer.putBytes(encodeSlotOffset, source, 0, length);
                encodeSlotOffset += length;
                encodeSlotFlags = flags;
                encodeSlotTraceId = traceId;
                encodeSlotAuthorization = authorization;

                flushEncodeSlot();
            }
        }

        private void flushEncodeSlot()
        {
            if (flushingRequest || encodeSlot == NO_SLOT)
            {
                return;
            }

            flushingRequest = true;
            try
            {
                final MutableDirectBufferEx buffer = encodePool.buffer(encodeSlot);
                while (encodeSlotSent < encodeSlotOffset)
                {
                    final long available = initialAvailable();
                    if (available <= 0)
                    {
                        break;
                    }

                    final int remaining = encodeSlotOffset - encodeSlotSent;
                    final int sliceLength = (int) Math.min(remaining, available);
                    if (sliceLength <= 0)
                    {
                        break;
                    }

                    final boolean first = encodeSlotSent == 0;
                    final boolean last = encodeSlotSent + sliceLength == encodeSlotOffset;
                    final int flags = sliceFlags(encodeSlotFlags, first, last);

                    final int sliceOffset = encodeSlotSent;
                    encodeSlotSent += sliceLength;
                    doAppData(encodeSlotTraceId, encodeSlotAuthorization, flags, buffer, sliceOffset, sliceLength);
                }

                if (encodeSlotSent >= encodeSlotOffset)
                {
                    encodePool.release(encodeSlot);
                    encodeSlot = NO_SLOT;
                    encodeSlotOffset = 0;
                    encodeSlotSent = 0;
                }
            }
            finally
            {
                flushingRequest = false;
            }
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
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);
            final String responseContentType = llmBeginEx != null ? llmBeginEx.contentType().asString() : null;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = encodePool.slotCapacity();
            state = LlmState.openingReply(state);

            encoder = codecs.createEncoder(responseContentType);

            server.doNetBegin(traceId, authorization, affinity, responseContentType);
            doAppWindow(traceId);
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            replySeq = data.sequence() + reserved;

            if (encoder == null)
            {
                server.doNetData(payload, flags, budgetId, traceId, authorization);
            }
            else
            {
                final boolean first = (flags & FLAG_INIT) != 0;
                final boolean last = (flags & FLAG_FIN) != 0;

                if (first)
                {
                    final OctetsFW extension = data.extension();
                    final LlmDataExFW llmDataEx = extension.get(llmDataExRO::tryWrap);
                    pendingResponseEvent = llmDataEx != null && llmDataEx.type() != null
                        ? llmDataEx.type().asString()
                        : null;
                }

                int position = 0;
                if (first)
                {
                    position += encoder.encodeEventName(pendingResponseEvent, copyBuffer, position, copyBuffer.capacity());
                }
                if (payload.sizeof() > 0)
                {
                    position += encoder.encodeData(payload.buffer(), payload.offset(), payload.sizeof(),
                        copyBuffer, position, copyBuffer.capacity());
                }
                if (last)
                {
                    position += encoder.encodeFlush(EMPTY_OCTETS.buffer(), 0, 0, copyBuffer, position, copyBuffer.capacity());
                }

                if (position > 0)
                {
                    server.doNetData(copyBuffer, 0, position, FLAG_INIT | FLAG_FIN, budgetId, traceId, authorization);
                }

                if (last)
                {
                    pendingResponseEvent = null;
                }
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final OctetsFW extension = end.extension();

            replySeq = end.sequence();
            state = LlmState.closeReply(state);

            if (server.encodeSlot != NO_SLOT)
            {
                server.state = LlmState.deferReplyEnd(server.state);
                server.pendingReplyEndTraceId = traceId;
                server.pendingReplyEndAuthorization = authorization;
            }
            else
            {
                server.doNetEnd(traceId, authorization, extension);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();

            replySeq = abort.sequence();
            state = LlmState.closeReply(state);

            server.doNetAbort(traceId, authorization, extension);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = LlmState.closeInitial(state);

            server.doNetReset(traceId);
            server.cleanup(traceId);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();

            initialAck = window.acknowledge();
            initialMax = window.maximum();
            initialBud = window.budgetId();
            initialPad = window.padding();
            state = LlmState.openInitial(state);

            flushEncodeSlot();

            if (encodeSlot == NO_SLOT)
            {
                server.decodeNetwork(traceId);
            }
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            server.doNetChallenge(traceId, authorization, extension);
        }

        private void doAppBegin(
            long traceId,
            long authorization)
        {
            this.initialId = supplyInitialId.applyAsLong(server.exitId);
            this.replyId = supplyReplyId.applyAsLong(initialId);

            final DirectBufferEx modelValue = server.envelope.get(ENVELOPE_MODEL, 0);
            final String model = modelValue != null
                ? modelValue.getStringWithoutLengthUtf8(0, modelValue.capacity())
                : null;

            final LlmBeginExFW.Builder builder = llmBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(llmTypeId)
                .dialect(server.dialect.name());

            if (server.contentType != null)
            {
                builder.contentType(server.contentType);
            }

            if (model != null)
            {
                builder.model(model);
            }

            final LlmBeginExFW llmBeginEx = builder.build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(llmBeginEx.buffer(), llmBeginEx.offset(), llmBeginEx.sizeof())
                .build();

            app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            state = LlmState.openingInitial(state);
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            MutableDirectBufferEx buffer,
            int offset,
            int length)
        {
            final int reserved = length;

            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(initialBud)
                .reserved(reserved)
                .payload(buffer, offset, length)
                .build();

            app.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            initialSeq += reserved;
        }

        private void doAppEnd(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppAbort(
            long traceId)
        {
            if (!LlmState.initialClosed(state))
            {
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(server.routedId)
                    .routedId(server.exitId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .build();

                app.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
                state = LlmState.closeInitial(state);
            }
        }

        private void doAppWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            app.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(server.routedId)
                .routedId(server.exitId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

            app.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void cleanup()
        {
            if (encodeSlot != NO_SLOT)
            {
                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotSent = 0;
            }
        }
    }

    private final class LlmUnauthorizedResponder
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final DirectBufferEx body;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private boolean began;
        private int bodySent;

        private LlmUnauthorizedResponder(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            LlmDialect dialect)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.body = asBuffer(dialect.unauthorizedBody());
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
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = transformBuffer.capacity();

            doNetWindow(traceId);
            doNetBegin(traceId, authorization);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();

            initialSeq = data.sequence() + data.reserved();
            initialAck = initialSeq;

            doNetWindow(traceId);
        }

        private void onNetEnd(
            EndFW end)
        {
            initialSeq = end.sequence();
            initialAck = initialSeq;
        }

        private void onNetAbort(
            AbortFW abort)
        {
            initialSeq = abort.sequence();
            initialAck = initialSeq;
        }

        private void onNetWindow(
            WindowFW window)
        {
            replyAck = window.acknowledge();
            replyMax = window.maximum();

            flushBody(window.traceId(), window.authorization());
        }

        private void doNetWindow(
            long traceId)
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialAck)
                .maximum(initialMax)
                .traceId(traceId)
                .budgetId(0L)
                .padding(0)
                .build();

            network.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        private void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HEADER_STATUS).value(STATUS_UNAUTHORIZED))
                .headersItem(h -> h.name(HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .build();

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(0L)
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            began = true;
        }

        private void flushBody(
            long traceId,
            long authorization)
        {
            if (!began || bodySent >= body.capacity())
            {
                return;
            }

            while (bodySent < body.capacity())
            {
                final long available = replyMax - (replySeq - replyAck);
                if (available <= 0)
                {
                    break;
                }

                final int remaining = body.capacity() - bodySent;
                final int length = (int) Math.min(remaining, available);
                final boolean first = bodySent == 0;
                final boolean last = bodySent + length == body.capacity();
                final int flags = (first ? FLAG_INIT : 0) | (last ? FLAG_FIN : 0);

                final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .flags(flags)
                    .budgetId(0L)
                    .reserved(length)
                    .payload(body, bodySent, length)
                    .extension(EMPTY_OCTETS)
                    .build();

                network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
                replySeq += length;
                bodySent += length;
            }

            if (bodySent >= body.capacity())
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .maximum(replyMax)
                .traceId(traceId)
                .authorization(authorization)
                .extension(EMPTY_OCTETS)
                .build();

            network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }
    }
}
