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
package io.aklivity.zilla.runtime.binding.agui.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.AguiConfiguration;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiEventDecoder;
import io.aklivity.zilla.runtime.binding.agui.internal.codec.AguiEventSink;
import io.aklivity.zilla.runtime.binding.agui.internal.config.AguiClientBindingConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.config.AguiRouteConfig;
import io.aklivity.zilla.runtime.binding.agui.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.agui.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiAbortExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiResetExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class AguiClientFactory implements AguiStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String AGUI_TYPE_NAME = "agui";

    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();
    private static final int EVENT_RECORD_HEADER = 3 * Integer.BYTES;
    private static final int REPLY_PADDING = EVENT_RECORD_HEADER + AguiEventDecoder.EX_MAX;

    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_SCHEME = ":scheme";
    private static final String HTTP_HEADER_AUTHORITY = ":authority";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_STATUS = ":status";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_ACCEPT = "accept";
    private static final String HTTP_HEADER_AUTHORIZATION = "authorization";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final long NET_AUTHORIZATION = 0L;
    private static final int STATUS_OK = 200;
    private static final int STATUS_BAD_GATEWAY = 502;

    private static final String ERROR_BAD_GATEWAY = "Bad Gateway";
    private static final String MEMBER_ERROR = "error";
    private static final String MEMBER_CODE = "code";
    private static final String CODE_AGENT_ABORTED = "agent_aborted";
    private static final String MESSAGE_AGENT_ABORTED = "Agent aborted";

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final AguiBeginExFW aguiBeginExRO = new AguiBeginExFW();
    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBufferEx(), 0, 0);

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final AguiBeginExFW.Builder aguiBeginExRW = new AguiBeginExFW.Builder();
    private final AguiEndExFW.Builder aguiEndExRW = new AguiEndExFW.Builder();
    private final AguiAbortExFW.Builder aguiAbortExRW = new AguiAbortExFW.Builder();
    private final AguiResetExFW.Builder aguiResetExRW = new AguiResetExFW.Builder();

    private final EngineContext context;
    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx codecBuffer;
    private final MutableDirectBufferEx eventBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int aguiTypeId;
    private final BufferPool decodePool;
    private final BufferPool encodePool;
    private final BufferPool replyPool;
    private final int decodeMax;
    private final int encodeMax;

    private int eventReserved;

    private final Long2ObjectHashMap<AguiClientBindingConfig> bindings;

    public AguiClientFactory(
        AguiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.codecBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.aguiTypeId = context.supplyTypeId(AGUI_TYPE_NAME);
        this.decodePool = context.bufferPool();
        this.encodePool = context.bufferPool().duplicate();
        this.replyPool = context.bufferPool().duplicate();
        this.decodeMax = decodePool.slotCapacity();
        this.encodeMax = encodePool.slotCapacity();
        this.eventBuffer = new UnsafeBufferEx(new byte[2 * encodeMax + REPLY_PADDING]);
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new AguiClientBindingConfig(binding, context));
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();

        MessageConsumer newStream = null;

        final AguiClientBindingConfig binding = bindings.get(routedId);
        final AguiRouteConfig route = binding != null ? binding.resolve(authorization) : null;
        final AguiBeginExFW beginEx = route != null ? begin.extension().get(aguiBeginExRO::tryWrap) : null;
        if (beginEx != null)
        {
            newStream = new AguiClient(sender, originId, routedId, initialId, authorization, route.id, binding,
                beginEx.threadId().asString(), beginEx.runId().asString(), beginEx.parentRunId().asString())::onAppMessage;
        }

        return newStream;
    }

    private static String header(
        HttpBeginExFW httpBeginEx,
        String name)
    {
        return Optional.ofNullable(httpBeginEx.headers().matchFirst(h -> name.equals(h.name().asString())))
            .map(h -> h.value().asString())
            .orElse(null);
    }

    private final class AguiClient implements AguiEventSink
    {
        private final MessageConsumer app;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long authorization;
        private final long resolvedId;
        private final AguiClientBindingConfig binding;
        private final String threadId;
        private final String runId;
        private final String parentRunId;

        private MessageConsumer net;
        private long netInitialId;
        private long netReplyId;

        private int state;
        private int netState;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private long netInitialSeq;
        private long netInitialAck;
        private int netInitialMax;
        private int netInitialPad;
        private long netInitialBud;

        private long netReplySeq;
        private long netReplyAck;
        private int netReplyMax;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private int replySlot = NO_SLOT;
        private int replySlotOffset;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private long decodeTraceId;
        private int netStatus;
        private AguiEventDecoder decoder;
        private byte[] terminalEx;

        private AguiClient(
            MessageConsumer app,
            long originId,
            long routedId,
            long initialId,
            long authorization,
            long resolvedId,
            AguiClientBindingConfig binding,
            String threadId,
            String runId,
            String parentRunId)
        {
            this.app = app;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
            this.resolvedId = resolvedId;
            this.binding = binding;
            this.threadId = threadId;
            this.runId = runId;
            this.parentRunId = parentRunId;
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

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = encodeMax;

            state = AguiState.openingInitial(state);

            doNetBegin(traceId, authorization);

            if (net != null)
            {
                doAppWindow(traceId, authorization);
            }
            else
            {
                doAppResetRejected(traceId, authorization, STATUS_BAD_GATEWAY, null, ERROR_BAD_GATEWAY);
            }
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                cleanupApp(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();

                if (payload != null && payload.sizeof() > 0)
                {
                    doNetData(traceId, authorization, payload.buffer(), payload.offset(), payload.limit());
                }

                flushAppWindow(traceId, authorization);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            initialSeq = end.sequence();
            state = AguiState.closingInitial(state);

            if (encodeSlot == NO_SLOT)
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            initialSeq = abort.sequence();
            state = AguiState.closedInitial(state);

            doNetAbort(traceId, authorization);
            doNetReset(traceId, authorization);
            doAppAbort(traceId, authorization, null);

            cleanupSlots();
        }

        private void onAppFlush(
            FlushFW flush)
        {
            initialSeq = flush.sequence() + flush.reserved();
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = window.budgetId();
            replyPad = window.padding();

            state = AguiState.openedReply(state);

            assert replyAck <= replySeq;

            if (replySlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = replyPool.buffer(replySlot);

                encodeReply(traceId, authorization, slot, 0, replySlotOffset);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = AguiState.closedReply(state);

            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            cleanupSlots();
        }

        private void doNetBegin(
            long traceId,
            long authorization)
        {
            netInitialId = supplyInitialId.applyAsLong(resolvedId);
            netReplyId = supplyReplyId.applyAsLong(netInitialId);

            final String credentials = binding.authorizationOf(authorization);

            net = newStream(this::onNetMessage, routedId, resolvedId, netInitialId,
                netInitialSeq, netInitialAck, netInitialMax, traceId, NET_AUTHORIZATION, 0L,
                httpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(httpTypeId)
                    .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                    .headersItem(h -> h.name(HTTP_HEADER_SCHEME).value(binding.serverScheme))
                    .headersItem(h -> h.name(HTTP_HEADER_AUTHORITY).value(binding.serverAuthority))
                    .headersItem(h -> h.name(HTTP_HEADER_PATH).value(binding.serverPath))
                    .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                    .headersItem(h -> h.name(HTTP_HEADER_ACCEPT).value(CONTENT_TYPE_EVENT_STREAM))
                    .headersItem(h -> h.name(HTTP_HEADER_AUTHORIZATION).value(credentials))
                    .build());

            netState = AguiState.openingInitial(netState);
        }

        private void doNetData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);

                if (encodeSlotOffset + limit - offset > encodeMax)
                {
                    cleanupApp(traceId, authorization);
                }
                else
                {
                    encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                    encodeSlotOffset += limit - offset;

                    encodeNet(traceId, authorization, encodeBuffer, 0, encodeSlotOffset);
                }
            }
            else
            {
                encodeNet(traceId, authorization, buffer, offset, limit);
            }
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int netWin = netInitialMax - (int) (netInitialSeq - netInitialAck);
            final int length = Math.max(Math.min(netWin - netInitialPad, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length + netInitialPad;

                doData(net, routedId, resolvedId, netInitialId, netInitialSeq, netInitialAck, netInitialMax,
                    traceId, NET_AUTHORIZATION, COMPLETE, netInitialBud, reserved, buffer, offset, length, null, 0, 0);

                netInitialSeq += reserved;
            }

            assert netInitialSeq <= netInitialAck + netInitialMax :
                String.format("%d <= %d + %d", netInitialSeq, netInitialAck, netInitialMax);

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = encodePool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupApp(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx encodeBuffer = encodePool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (AguiState.initialClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
            }
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (net != null && AguiState.initialOpening(netState) && !AguiState.initialClosed(netState))
            {
                netState = AguiState.closedInitial(netState);
                doEnd(net, routedId, resolvedId, netInitialId, netInitialSeq, netInitialAck, netInitialMax,
                    traceId, NET_AUTHORIZATION);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (net != null && AguiState.initialOpening(netState) && !AguiState.initialClosed(netState))
            {
                netState = AguiState.closedInitial(netState);
                doAbort(net, routedId, resolvedId, netInitialId, netInitialSeq, netInitialAck, netInitialMax,
                    traceId, NET_AUTHORIZATION);
            }
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (net != null && !AguiState.replyClosed(netState))
            {
                netState = AguiState.closedReply(netState);
                doReset(net, routedId, resolvedId, netReplyId, netReplySeq, netReplyAck, netReplyMax,
                    traceId, NET_AUTHORIZATION, emptyRO);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            int padding,
            int capabilities)
        {
            netState = AguiState.openedReply(netState);

            doWindow(net, routedId, resolvedId, netReplyId, netReplySeq, netReplyAck, netReplyMax,
                traceId, NET_AUTHORIZATION, 0L, padding, capabilities);
        }

        private void flushNetWindow(
            long traceId,
            long authorization)
        {
            if (net != null && !AguiState.replyClosed(netState) && decoder != null)
            {
                final long netReplyAckMax = Math.max(netReplySeq - replySlotOffset, netReplyAck);

                if (netReplyAckMax > netReplyAck)
                {
                    netReplyAck = netReplyAckMax;

                    assert netReplyAck <= netReplySeq;

                    doNetWindow(traceId, authorization, REPLY_PADDING, FRAMING_CAPABILITIES_MASK);
                }
            }
        }

        private void flushAppWindow(
            long traceId,
            long authorization)
        {
            final long initialAckMax = Math.max(initialSeq - encodeSlotOffset, initialAck);

            if (initialAckMax > initialAck)
            {
                initialAck = initialAckMax;

                assert initialAck <= initialSeq;

                doAppWindow(traceId, authorization);
            }
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
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
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

            netReplySeq = begin.sequence();
            netReplyAck = begin.acknowledge();
            netState = AguiState.openingReply(netState);

            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.sizeof() > 0 ? extension.get(httpBeginExRO::tryWrap) : null;
            final String status = httpBeginEx != null ? header(httpBeginEx, HTTP_HEADER_STATUS) : null;
            final String contentType = httpBeginEx != null ? header(httpBeginEx, HTTP_HEADER_CONTENT_TYPE) : null;

            netStatus = statusOf(status);

            if (netStatus == STATUS_OK && contentType != null && contentType.startsWith(CONTENT_TYPE_EVENT_STREAM))
            {
                decoder = new AguiEventDecoder(this, aguiTypeId, decodeMax);
                netReplyMax = encodeMax;

                doNetWindow(traceId, authorization, REPLY_PADDING, FRAMING_CAPABILITIES_MASK);
            }
            else
            {
                netStatus = netStatus == STATUS_OK ? STATUS_BAD_GATEWAY : netStatus;
                netReplyMax = decodeMax;

                doNetWindow(traceId, authorization, 0, 0);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= netReplySeq;
            assert acknowledge <= netReplyAck;

            netReplySeq = sequence + data.reserved();

            assert netReplyAck <= netReplySeq;

            if (netReplySeq > netReplyAck + netReplyMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                final OctetsFW payload = data.payload();
                final DirectBufferEx buffer = payload != null ? payload.buffer() : emptyRO.buffer();
                final int offset = payload != null ? payload.offset() : 0;
                final int limit = payload != null ? payload.limit() : 0;

                if (decoder != null)
                {
                    decodeTraceId = traceId;
                    decoder.decode(data.flags(), buffer, offset, limit);
                    flushNetWindow(traceId, authorization);
                }
                else
                {
                    onNetBody(traceId, authorization, buffer, offset, limit);
                }
            }
        }

        private void onNetBody(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (decodeSlot == NO_SLOT)
            {
                decodeSlot = decodePool.acquire(initialId);
            }

            if (decodeSlot != NO_SLOT && decodeSlotOffset + limit - offset <= decodeMax)
            {
                final MutableDirectBufferEx decodeBuffer = decodePool.buffer(decodeSlot);
                decodeBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                decodeSlotOffset += limit - offset;
            }

            netReplyAck = netReplySeq;

            doNetWindow(traceId, authorization, 0, 0);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            netState = AguiState.closedReply(netState);

            if (decoder != null)
            {
                decodeTraceId = traceId;
                decoder.finish();
            }
            else
            {
                doAppResetRejected(traceId, authorization, netStatus, errorCode(), errorMessage());
                doNetAbort(traceId, authorization);
            }

            cleanupDecodeSlot();
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            netState = AguiState.closedReply(netState);

            if (AguiState.replyOpening(state))
            {
                doAppAbortTerminal(traceId, authorization, abortEx(CODE_AGENT_ABORTED, MESSAGE_AGENT_ABORTED));
            }
            else
            {
                doAppResetRejected(traceId, authorization, STATUS_BAD_GATEWAY, null, ERROR_BAD_GATEWAY);
            }

            doNetAbort(traceId, authorization);
            cleanupDecodeSlot();
        }

        private void onNetFlush(
            FlushFW flush)
        {
            netReplySeq = flush.sequence() + flush.reserved();
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge >= netInitialAck;
            assert maximum >= netInitialMax;

            netInitialAck = acknowledge;
            netInitialMax = maximum;
            netInitialBud = window.budgetId();
            netInitialPad = window.padding();

            netState = AguiState.openedInitial(netState);

            assert netInitialAck <= netInitialSeq;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = encodePool.buffer(encodeSlot);

                encodeNet(traceId, authorization, slot, 0, encodeSlotOffset);
            }

            flushAppWindow(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            netState = AguiState.closedInitial(netState);

            if (AguiState.replyOpening(state))
            {
                doAppAbortTerminal(traceId, authorization, abortEx(CODE_AGENT_ABORTED, MESSAGE_AGENT_ABORTED));
            }
            else
            {
                doAppResetRejected(traceId, authorization, STATUS_BAD_GATEWAY, null, ERROR_BAD_GATEWAY);
            }

            doNetReset(traceId, authorization);
            cleanupEncodeSlot();
        }

        @Override
        public void runStarted(
            String threadId,
            String runId,
            String parentRunId)
        {
            doAppBegin(decodeTraceId, authorization, threadId, runId, parentRunId);
        }

        @Override
        public void runFinished(
            AguiOutcomeType outcome,
            long timestamp,
            String detail)
        {
            final byte[] detailBytes = detail != null ? detail.getBytes(UTF_8) : null;
            final AguiEndExFW.Builder builder = aguiEndExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(aguiTypeId)
                .timestamp(timestamp)
                .outcome(o -> o.set(outcome));
            final AguiEndExFW endEx = detailBytes != null
                ? builder.detailLength(detailBytes.length)
                    .detail(new UnsafeBufferEx(detailBytes), 0, detailBytes.length)
                    .build()
                : builder.build();

            doAppEndTerminal(decodeTraceId, authorization, bytesOf(endEx));
        }

        @Override
        public void runError(
            String message,
            String code)
        {
            doAppAbortTerminal(decodeTraceId, authorization, abortEx(code, message));
        }

        @Override
        public void message(
            int flags,
            AguiDataExFW dataEx,
            DirectBufferEx payload,
            int offset,
            int limit)
        {
            final int extensionLength = dataEx != null ? dataEx.sizeof() : 0;
            final int payloadLength = limit - offset;
            int progress = EVENT_RECORD_HEADER;

            if (extensionLength > 0)
            {
                eventBuffer.putBytes(progress, dataEx.buffer(), dataEx.offset(), extensionLength);
                progress += extensionLength;
            }

            eventBuffer.putBytes(progress, payload, offset, payloadLength);
            progress += payloadLength;

            eventBuffer.putInt(0, flags);
            eventBuffer.putInt(Integer.BYTES, extensionLength);
            eventBuffer.putInt(2 * Integer.BYTES, payloadLength);

            doAppData(decodeTraceId, authorization, eventBuffer, 0, progress);
        }

        @Override
        public void failed(
            String code,
            String message)
        {
            final long traceId = decodeTraceId;

            doAppAbortTerminal(traceId, authorization, abortEx(code, message));
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            String threadId,
            String runId,
            String parentRunId)
        {
            if (!AguiState.replyOpening(state))
            {
                state = AguiState.openingReply(state);

                doBegin(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, 0L,
                    aguiBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                        .typeId(aguiTypeId)
                        .threadId(threadId)
                        .runId(runId)
                        .parentRunId(parentRunId)
                        .build());
            }
        }

        private void doAppData(
            long traceId,
            long authorization,
            DirectBufferEx buffer,
            int offset,
            int limit)
        {
            if (replySlot != NO_SLOT)
            {
                final MutableDirectBufferEx slot = replyPool.buffer(replySlot);

                if (replySlotOffset + limit - offset > encodeMax)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    slot.putBytes(replySlotOffset, buffer, offset, limit - offset);
                    replySlotOffset += limit - offset;

                    encodeReply(traceId, authorization, slot, 0, replySlotOffset);
                }
            }
            else
            {
                encodeReply(traceId, authorization, (MutableDirectBufferEx) buffer, offset, limit);
            }
        }

        private void encodeReply(
            long traceId,
            long authorization,
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int length = doDataEvents(app, originId, routedId, replyId, replySeq, replyAck, replyMax, replyPad, replyBud,
                traceId, authorization, buffer, offset, limit);

            replySeq += eventReserved;

            assert replySeq <= replyAck + replyMax :
                String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (replySlot == NO_SLOT)
                {
                    replySlot = replyPool.acquire(replyId);
                }

                if (replySlot == NO_SLOT)
                {
                    cleanupNet(traceId, authorization);
                }
                else
                {
                    final MutableDirectBufferEx slot = replyPool.buffer(replySlot);
                    slot.putBytes(0, buffer, offset + length, remaining);
                    replySlotOffset = remaining;
                }
            }
            else
            {
                cleanupReplySlot();

                if (AguiState.replyAborting(state))
                {
                    doAppAbort(traceId, authorization, terminalEx);
                }
                else if (AguiState.replyClosing(state))
                {
                    doAppEnd(traceId, authorization, terminalEx);
                }
            }

            flushNetWindow(traceId, authorization);
        }

        private void doAppEndTerminal(
            long traceId,
            long authorization,
            byte[] endEx)
        {
            doAppBegin(traceId, authorization, threadId, runId, parentRunId);

            terminalEx = endEx;
            state = AguiState.closingReply(state);

            if (replySlot == NO_SLOT)
            {
                doAppEnd(traceId, authorization, terminalEx);
            }
        }

        private void doAppAbortTerminal(
            long traceId,
            long authorization,
            byte[] abortEx)
        {
            if (!AguiState.replyClosed(state) && !AguiState.replyClosing(state) && !AguiState.replyAborting(state))
            {
                doAppBegin(traceId, authorization, threadId, runId, parentRunId);

                terminalEx = abortEx;
                state = AguiState.abortingReply(state);

                if (replySlot == NO_SLOT)
                {
                    doAppAbort(traceId, authorization, terminalEx);
                }
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization,
            byte[] extension)
        {
            if (!AguiState.replyClosed(state))
            {
                state = AguiState.closedReply(state);

                doEnd(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    extension);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization,
            byte[] extension)
        {
            if (AguiState.replyOpening(state) && !AguiState.replyClosed(state))
            {
                state = AguiState.closedReply(state);

                doAbort(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                    extension);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization)
        {
            doWindow(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0L, 0, 0);
        }

        private void doAppResetRejected(
            long traceId,
            long authorization,
            int status,
            String code,
            String message)
        {
            if (!AguiState.initialClosed(state))
            {
                state = AguiState.closedInitial(state);

                final AguiResetExFW resetEx = aguiResetExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(aguiTypeId)
                    .error(e -> e.status(status).code(code).message(message))
                    .build();

                doReset(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, resetEx);
            }
        }

        private byte[] abortEx(
            String code,
            String message)
        {
            final AguiAbortExFW abortEx = aguiAbortExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(aguiTypeId)
                .error(e -> e.code(code).message(message))
                .build();

            return bytesOf(abortEx);
        }

        private byte[] bytesOf(
            Flyweight flyweight)
        {
            final byte[] bytes = new byte[flyweight.sizeof()];
            flyweight.buffer().getBytes(flyweight.offset(), bytes);

            return bytes;
        }

        private String errorMessage()
        {
            final JsonObject error = errorBody();
            final JsonValue message = error != null ? error.get(MEMBER_ERROR) : null;

            return message instanceof JsonString string ? string.getString() : null;
        }

        private String errorCode()
        {
            final JsonObject error = errorBody();
            final JsonValue code = error != null ? error.get(MEMBER_CODE) : null;

            return code instanceof JsonString string ? string.getString() : null;
        }

        private JsonObject errorBody()
        {
            JsonObject body = null;

            if (decodeSlot != NO_SLOT)
            {
                try
                {
                    body = Json.createReader(new StringReader(
                        decodePool.buffer(decodeSlot).getStringWithoutLengthUtf8(0, decodeSlotOffset))).readObject();
                }
                catch (JsonException | IllegalStateException ex)
                {
                    body = null;
                }
            }

            return body;
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppResetRejected(traceId, authorization, STATUS_BAD_GATEWAY, null, ERROR_BAD_GATEWAY);
            doAppAbortTerminal(traceId, authorization, abortEx(CODE_AGENT_ABORTED, MESSAGE_AGENT_ABORTED));

            doNetAbort(traceId, authorization);
            doNetReset(traceId, authorization);

            cleanupSlots();
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            doAppAbortTerminal(traceId, authorization, abortEx(CODE_AGENT_ABORTED, MESSAGE_AGENT_ABORTED));
            doAppResetRejected(traceId, authorization, STATUS_BAD_GATEWAY, null, ERROR_BAD_GATEWAY);

            cleanupSlots();
        }

        private void cleanupSlots()
        {
            cleanupEncodeSlot();
            cleanupReplySlot();
            cleanupDecodeSlot();
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

        private void cleanupReplySlot()
        {
            if (replySlot != NO_SLOT)
            {
                replyPool.release(replySlot);
                replySlot = NO_SLOT;
                replySlotOffset = 0;
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
    }

    private static int statusOf(
        String status)
    {
        int value = STATUS_BAD_GATEWAY;

        if (status != null)
        {
            try
            {
                value = Integer.parseInt(status);
            }
            catch (NumberFormatException ex)
            {
                value = STATUS_BAD_GATEWAY;
            }
        }

        return value;
    }

    private int doDataEvents(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        int padding,
        long budgetId,
        long traceId,
        long authorization,
        MutableDirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;
        long replySeq = sequence;
        boolean blocked = false;

        while (!blocked && progress < limit)
        {
            final int flags = buffer.getInt(progress);
            final int extensionLength = buffer.getInt(progress + Integer.BYTES);
            final int payloadLength = buffer.getInt(progress + 2 * Integer.BYTES);
            final int extensionAt = progress + EVENT_RECORD_HEADER;
            final int payloadAt = extensionAt + extensionLength;

            final int window = maximum - (int) (replySeq - acknowledge) - padding;
            final boolean oversized = payloadLength > maximum - padding;
            final int length = oversized || payloadLength <= window ? Math.min(Math.max(window, 0), payloadLength) : 0;

            if (window >= 0 && (length > 0 || payloadLength == 0))
            {
                final boolean whole = length == payloadLength;
                final int reserved = length + padding;

                doData(receiver, originId, routedId, streamId, replySeq, acknowledge, maximum,
                    traceId, authorization, whole ? flags : flags & ~FIN, budgetId, reserved,
                    buffer, payloadAt, length, buffer, extensionAt, extensionLength);
                replySeq += reserved;

                if (whole)
                {
                    progress = payloadAt + payloadLength;
                }
                else
                {
                    final int remainderAt = payloadAt + length - EVENT_RECORD_HEADER;
                    buffer.putInt(remainderAt, flags & ~INIT);
                    buffer.putInt(remainderAt + Integer.BYTES, 0);
                    buffer.putInt(remainderAt + 2 * Integer.BYTES, payloadLength - length);
                    progress = remainderAt;
                    blocked = true;
                }
            }
            else
            {
                blocked = true;
            }
        }

        eventReserved = (int) (replySeq - sequence);

        return progress - offset;
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
        DirectBufferEx extension,
        int extensionOffset,
        int extensionLength)
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
            .reserved(reserved);

        if (payload != null)
        {
            builder.payload(payload, offset, length);
        }
        else
        {
            builder.payload((OctetsFW) null);
        }

        if (extensionLength > 0 && (flags & INIT) != 0)
        {
            builder.extension(extension, extensionOffset, extensionLength);
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
        long authorization)
    {
        doEnd(receiver, originId, routedId, streamId, sequence, acknowledge, maximum, traceId, authorization, null);
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
        byte[] extension)
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
            builder.extension(new UnsafeBufferEx(extension), 0, extension.length);
        }

        final EndFW end = builder.build();

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
        long authorization)
    {
        doAbort(receiver, originId, routedId, streamId, sequence, acknowledge, maximum, traceId, authorization, null);
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
        byte[] extension)
    {
        final AbortFW.Builder builder = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            builder.extension(new UnsafeBufferEx(extension), 0, extension.length);
        }

        final AbortFW abort = builder.build();

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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
