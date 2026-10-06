/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.sse.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.SseBinding;
import io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseBindingConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.config.SseRouteConfig;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.sse.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.Capability;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.HttpDataExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.SseDataExFW;
import io.aklivity.zilla.runtime.binding.sse.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class SseClientFactory implements SseStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";

    private static final String8FW HTTP_HEADER_METHOD = new String8FW(":method");
    private static final String8FW HTTP_HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HTTP_HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HTTP_HEADER_PATH = new String8FW(":path");
    private static final String8FW HTTP_HEADER_ACCEPT = new String8FW("accept");
    private static final String8FW HTTP_HEADER_LAST_EVENT_ID = new String8FW("last-event-id");
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");

    private static final String16FW HTTP_HEADER_METHOD_GET = new String16FW("GET");
    private static final String16FW HTTP_HEADER_ACCEPT_TEXT_EVENT_STREAM = new String16FW("text/event-stream");
    private static final String16FW HTTP_HEADER_STATUS_200 = new String16FW("200");

    private static final String8FW HTTP_HEADER_EVENT_ID = new String8FW("id");
    private static final String8FW HTTP_HEADER_EVENT_TYPE = new String8FW("type");

    private static final int FRAMING_CAPABILITIES_MASK = 1 << Capability.FRAMING.ordinal();

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(0L, 0), 0, 0);

    private static final String8FW FIELD_VALUE_NULL = new String8FW(null);

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
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final SseBeginExFW sseBeginExRO = new SseBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpDataExFW httpDataExRO = new HttpDataExFW();

    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final SseDataExFW.Builder sseDataExRW = new SseDataExFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final MutableDirectBufferEx extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int sseTypeId;

    private final Long2ObjectHashMap<SseBindingConfig> bindings;

    public SseClientFactory(
        SseConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBufferEx(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.sseTypeId = context.supplyTypeId(SseBinding.NAME);
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
        SseBindingConfig sseBinding = new SseBindingConfig(binding);
        bindings.put(binding.id, sseBinding);
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
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);

        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final String16FW path = sseBeginEx.path();

        MessageConsumer newStream = null;

        final SseBindingConfig binding = bindings.get(routedId);
        final SseRouteConfig resolved = binding != null ?  binding.resolve(authorization, path.asString()) : null;

        if (resolved != null)
        {
            newStream = new SseClient(
                application,
                originId,
                routedId,
                initialId,
                resolved.id)::onAppMessage;

        }

        return newStream;
    }

    private final class SseClient
    {
        private final MessageConsumer application;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final HttpClient delegate;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private SseClient(
            MessageConsumer application,
            long originId,
            long routedId,
            long initialId,
            long resolvedId)
        {
            this.application = application;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new HttpClient(routedId, resolvedId, this);
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
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onAppEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();

            final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);
            final String16FW scheme = sseBeginEx.scheme();
            final String16FW authority = sseBeginEx.authority();
            final String16FW path = sseBeginEx.path();
            final String8FW lastId = sseBeginEx.lastId();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;
            state = SseState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doNetBegin(traceId, affinity, acknowledge, scheme, authority, path, lastId);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = SseState.closedInitial(state);

            delegate.doNetEnd(traceId, authorization);
            delegate.cleanupNet(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = SseState.closedInitial(state);

            delegate.cleanupNet(traceId, authorization);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = SseState.openedReply(state);

            assert replyAck <= replySeq;

            delegate.doNetWindow(traceId, authorization, budgetId, padding);
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = SseState.closedReply(state);

            delegate.doNetReset(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!SseState.replyOpening(state))
            {
                replySeq = delegate.replySeq;
                state = SseState.openingReply(state);

                doBegin(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, affinity);
            }
        }

        private void doAppData(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            Flyweight extension)
        {
            doData(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, flags, reserved, payload, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doAbort(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doEnd(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = SseState.openedInitial(state);

            doWindow(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, padding, 0);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doReset(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void cleanupApp(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppBegin(traceId, authorization, 0L);
            doAppAbort(traceId, authorization);
        }
    }

    private final class HttpClient
    {
        private final SseClient delegate;

        private MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private HttpClient(
            long originId,
            long routedId,
            SseClient delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = delegate;
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String16FW scheme,
            String16FW authority,
            String16FW path,
            String8FW lastId)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = SseState.openingInitial(state);

            network = newHttpStream(this::onNetMessage, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax, traceId, authorization, affinity,
                    scheme, authority, path, lastId);
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doEnd(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!SseState.initialClosed(state))
            {
                state = SseState.closedInitial(state);

                doAbort(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = SseState.openedReply(state);

            replyAck = delegate.replyAck;
            replyMax = delegate.replyMax;

            doWindow(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding, FRAMING_CAPABILITIES_MASK);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!SseState.replyClosed(state))
            {
                state = SseState.closedReply(state);

                doReset(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization);
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
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
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
            final long affinity = begin.affinity();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            String16FW status = HTTP_HEADER_STATUS_200;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
                final HttpHeaderFW statusHeader = headers.matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name()));

                if (statusHeader != null)
                {
                    status = statusHeader.value();
                }
            }

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            state = SseState.openingReply(state);

            assert replyAck <= replySeq;

            if (!HTTP_HEADER_STATUS_200.equals(status))
            {
                delegate.doAppReset(traceId, authorization);
                doNetAbort(traceId, authorization);
            }
            else
            {
                delegate.doAppBegin(traceId, authorization, affinity);
            }
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                cleanupNet(traceId, authorization);
            }
            else
            {
                final HttpDataExFW httpDataEx = data.extension().get(httpDataExRO::tryWrap);

                delegate.doAppData(traceId, authorization, budgetId, data.flags(), data.reserved(), data.payload(),
                        sseDataEx(httpDataEx));
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = SseState.closedReply(state);

            delegate.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;

            assert replyAck <= replySeq;

            state = SseState.closedReply(state);

            delegate.doAppAbort(traceId, authorization);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + flush.reserved();

            assert replyAck <= replySeq;

            delegate.doAppFlush(traceId, authorization, budgetId, reserved);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = SseState.closedInitial(state);

            delegate.doAppReset(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            initialAck = acknowledge;
            initialMax = maximum;
            state = SseState.openedInitial(state);

            assert initialAck <= initialMax;

            delegate.doAppWindow(traceId, authorization, budgetId, padding);

            doNetEnd(traceId, authorization);
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            delegate.cleanupApp(traceId, authorization);
        }

        private Flyweight sseDataEx(
            HttpDataExFW httpDataEx)
        {
            Flyweight sseDataEx = EMPTY_OCTETS;

            if (httpDataEx != null)
            {
                final HttpHeaderFW idHeader = httpDataEx.headers().matchFirst(h -> HTTP_HEADER_EVENT_ID.equals(h.name()));
                final boolean hasId = idHeader != null;

                final SseDataExFW.Builder builder = sseDataExRW.wrap(extBuffer, 0, extBuffer.capacity()).typeId(sseTypeId);

                if (hasId)
                {
                    final DirectBufferEx id = idHeader.value().value();
                    builder.id(id, 0, id.capacity());
                }
                else
                {
                    builder.id(FIELD_VALUE_NULL);
                }

                final HttpHeaderFW typeHeader = httpDataEx.headers().matchFirst(h -> HTTP_HEADER_EVENT_TYPE.equals(h.name()));
                final boolean hasType = typeHeader != null;

                if (hasType)
                {
                    final DirectBufferEx type = typeHeader.value().value();
                    builder.type(type, 0, type.capacity());
                }
                else
                {
                    builder.type(FIELD_VALUE_NULL);
                }

                sseDataEx = hasId || hasType ? builder.build() : EMPTY_OCTETS;
            }

            return sseDataEx;
        }
    }

    private MessageConsumer newHttpStream(
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
        String16FW scheme,
        String16FW authority,
        String16FW path,
        String8FW lastId)
    {
        final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                .typeId(httpTypeId)
                .headers(hs ->
                {
                    hs.item(h -> h
                        .name(HTTP_HEADER_METHOD)
                        .value(HTTP_HEADER_METHOD_GET));
                    hs.item(h -> h
                        .name(HTTP_HEADER_SCHEME)
                        .value(scheme));
                    hs.item(h -> h
                        .name(HTTP_HEADER_AUTHORITY)
                        .value(authority));
                    hs.item(h -> h
                        .name(HTTP_HEADER_PATH)
                        .value(path));
                    hs.item(h -> h
                        .name(HTTP_HEADER_ACCEPT)
                        .value(HTTP_HEADER_ACCEPT_TEXT_EVENT_STREAM));

                    final DirectBufferEx lastIdBuf = lastId.value();
                    if (lastIdBuf != null)
                    {
                        hs.item(h -> h
                            .name(HTTP_HEADER_LAST_EVENT_ID)
                            .value(lastIdBuf, 0, lastIdBuf.capacity()));
                    }
                })
                .build();

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
                .extension(httpBeginEx.buffer(), httpBeginEx.offset(), httpBeginEx.sizeof())
                .build();

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

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
        long affinity)
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
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doEnd(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
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
                .build();

        sender.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
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
                .build();

        sender.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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
        int reserved)
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
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
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

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
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
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
