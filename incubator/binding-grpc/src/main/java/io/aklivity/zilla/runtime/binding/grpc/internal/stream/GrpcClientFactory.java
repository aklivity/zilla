/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcRouteConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.codec.GrpcMessageFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcKindFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public class GrpcClientFactory implements GrpcStreamFactory
{
    private static final int GRPC_MESSAGE_PADDING = 5;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final String HTTP_TYPE_NAME = "http";
    private static final String8FW HTTP_HEADER_METHOD = new String8FW(":method");
    private static final String8FW HTTP_HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HTTP_HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HTTP_HEADER_PATH = new String8FW(":path");
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");
    private static final String8FW HTTP_HEADER_GRPC_STATUS = new String8FW("grpc-status");
    private static final String8FW HTTP_HEADER_CONTENT_TYPE = new String8FW("content-type");

    private static final String16FW HTTP_HEADER_METHOD_POST = new String16FW("POST");
    private static final String16FW HTTP_HEADER_STATUS_200 = new String16FW("200");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_GRPC = new String16FW("application/grpc");
    private static final String16FW HEADER_VALUE_GRPC_OK = new String16FW("0");
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

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

    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final GrpcMessageFW grpcMessageRO = new GrpcMessageFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpEndExFW.Builder httpEndExRW = new HttpEndExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcMessageFW.Builder grpcMessageRW = new GrpcMessageFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer metadataBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int grpcTypeId;

    private final Long2ObjectHashMap<GrpcBindingConfig> bindings;

    public GrpcClientFactory(
        GrpcConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.metadataBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.grpcTypeId = context.supplyTypeId(GrpcBinding.NAME);
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        GrpcBindingConfig grpcBinding = new GrpcBindingConfig(binding, metadataBuffer);
        bindings.put(binding.id, grpcBinding);
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
        MessageConsumer application)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();
        final GrpcBeginExFW grpcBeginEx = extension.get(grpcBeginExRO::tryWrap);

        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final String service = grpcBeginEx.service().asString();
        final String method = grpcBeginEx.method().asString();
        Array32FW<GrpcMetadataFW> metadata = grpcBeginEx.metadata();

        MessageConsumer newStream = null;

        final GrpcBindingConfig binding = bindings.get(routeId);
        final GrpcRouteConfig resolved = binding != null ?
            binding.resolve(authorization, service, method, metadata) : null;

        if (resolved != null)
        {
            newStream = new GrpcClient(
                application,
                routeId,
                initialId,
                resolved.id,
                service,
                method,
                grpcBeginEx.request(),
                grpcBeginEx.response())::onAppMessage;

        }

        return newStream;
    }

    private final class GrpcClient
    {
        private final MessageConsumer application;
        private String service;
        private String method;
        private final GrpcKindFW request;
        private final GrpcKindFW response;
        private final HttpClient delegate;

        private final long routeId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private int state;

        private GrpcClient(
            MessageConsumer application,
            long routeId,
            long initialId,
            long resolvedId,
            String service,
            String method,
            GrpcKindFW request,
            GrpcKindFW response)
        {
            this.application = application;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = new HttpClient(resolvedId, this);
            this.service = service;
            this.method = method;
            this.request = request;
            this.response = response;
        }

        private void onAppMessage(
            int msgTypeId,
            DirectBuffer buffer,
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

            final GrpcBeginExFW grpcBeginEx = extension.get(grpcBeginExRO::tryWrap);
            final String16FW scheme = grpcBeginEx.scheme();
            final String16FW authority = grpcBeginEx.authority();
            final String path = String.format("/%s/%s", service, method);
            final Array32FW<GrpcMetadataFW> metadata = grpcBeginEx.metadata();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;
            initialMax = maximum;
            state = GrpcState.openingInitial(state);

            assert initialAck <= initialSeq;

            delegate.doNetBegin(traceId, affinity, acknowledge, path, scheme, authority, metadata);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            final GrpcDataExFW grpcDataEx = extension.get(grpcDataExRO::tryWrap);
            final int deferred = grpcDataEx != null ? grpcDataEx.deferred() : 0;
            delegate.doNetData(traceId, authorization, budgetId, reserved, deferred, flags, payload);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = GrpcState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = GrpcState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doNetAbort(traceId, authorization);
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
            replyPad = padding;
            state = GrpcState.openReply(state);

            delegate.doNetWindow(traceId, authorization, budgetId, padding, replyAck, replyMax);

            assert replyAck <= replySeq;

        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = GrpcState.closeReply(state);

            delegate.doNetReset(traceId, authorization);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            if (!GrpcState.replyOpening(state))
            {
                replySeq = delegate.replySeq;
                replyAck = delegate.replyAck;
                replyMax = delegate.replyMax;
                state = GrpcState.openingReply(state);

                doBegin(application, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity);
            }
        }

        private void doAppData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length,
            Flyweight extension)
        {
            doData(application, routeId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, extension);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closeReply(state);

                doAbort(application, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closeReply(state);

                doEnd(application, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            state = GrpcState.openInitial(state);

            doWindow(application, routeId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding + GRPC_MESSAGE_PADDING);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doReset(application, routeId, initialId, initialSeq, initialAck, initialMax,
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
        private final GrpcClient delegate;

        private MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;
        private int messageDeferred;

        private HttpClient(
            long routeId,
            GrpcClient delegate)
        {
            this.routeId = routeId;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.delegate = delegate;
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            String path,
            String16FW scheme,
            String16FW authority,
            Array32FW<GrpcMetadataFW> metadata)
        {
            initialSeq = delegate.initialSeq;
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;
            state = GrpcState.openingInitial(state);

            network = newHttpStream(this::onNetMessage, routeId, initialId,
                initialSeq, initialAck, initialMax, traceId, authorization, affinity, path,
                scheme, authority, metadata);
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int deferred,
            int flags,
            OctetsFW payload)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();
            final int payloadSize = payload.sizeof();

            int encodeProgress = encodeOffset;

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                GrpcMessageFW message = grpcMessageRW
                    .wrap(encodeBuffer, encodeOffset, encodeLimit)
                    .flag(0)
                    .length(payloadSize + deferred)
                    .build();
                encodeProgress = message.limit();
            }

            encodeBuffer.putBytes(encodeProgress, payload.buffer(), payload.offset(), payloadSize);
            encodeProgress += payloadSize;

            doData(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                budgetId, reserved, flags, encodeBuffer, encodeOffset, encodeProgress - encodeOffset, EMPTY_OCTETS);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doEnd(network, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doAbort(network, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            long replyAck,
            int replyMax)
        {
            this.replyAck = replyAck;
            this.replyMax = replyMax;

            state = GrpcState.openReply(state);

            doWindow(network, routeId, replyId, replySeq, this.replyAck, this.replyMax,
                traceId, authorization, budgetId, padding);

            assert this.replyAck <= this.replySeq;
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closeReply(state);

                doReset(network, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        private void onNetMessage(
            int msgTypeId,
            DirectBuffer buffer,
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
            final int maximum = begin.maximum();
            final OctetsFW extension = begin.extension();
            final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

            String16FW status = HTTP_HEADER_STATUS_200;
            String16FW grpcStatus = null;
            if (httpBeginEx != null)
            {
                final Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
                final HttpHeaderFW statusHeader = headers.matchFirst(h -> HTTP_HEADER_STATUS.equals(h.name()));

                if (statusHeader != null)
                {
                    status = statusHeader.value();
                }

                final HttpHeaderFW grpcStatusHeader = headers.matchFirst(h -> HTTP_HEADER_GRPC_STATUS.equals(h.name()));

                if (grpcStatusHeader != null)
                {
                    grpcStatus = grpcStatusHeader.value();
                }
            }

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = maximum;
            state = GrpcState.openingReply(state);

            assert replyAck <= replySeq;

            if (!HTTP_HEADER_STATUS_200.equals(status) ||
                !HTTP_HEADER_GRPC_STATUS.equals(grpcStatus))
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
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            int flags = data.flags();

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
                final DirectBuffer buffer = payload.buffer();
                final int offset = payload.offset();
                final int limit = payload.limit();
                final int size = payload.sizeof();

                if (messageDeferred == 0)
                {
                    final GrpcMessageFW grpcMessage = grpcMessageRO.wrap(buffer, offset, limit);
                    final int messageLength = grpcMessage.length();
                    final int payloadSize = size - GRPC_MESSAGE_PADDING;
                    messageDeferred = messageLength - payloadSize;

                    Flyweight dataEx = messageDeferred > 0 ?
                        grpcDataExRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                            .typeId(grpcTypeId)
                            .deferred(messageDeferred)
                            .build() : EMPTY_OCTETS;


                    flags = messageDeferred > 0 ? flags & ~DATA_FLAG_INIT : flags;
                    delegate.doAppData(traceId, authorization, budgetId, reserved, flags,
                        buffer, offset + GRPC_MESSAGE_PADDING, payloadSize, dataEx);
                }
                else
                {
                    messageDeferred -= size;
                    assert messageDeferred >= 0;

                    flags = messageDeferred > 0 ? flags & ~DATA_FLAG_INIT : flags;

                    delegate.doAppData(traceId, authorization, budgetId, reserved, flags,
                        buffer, offset, size, EMPTY_OCTETS);
                }
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

            state = GrpcState.closeReply(state);

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

            state = GrpcState.closeReply(state);

            delegate.doAppAbort(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = GrpcState.closeInitial(state);

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
            state = GrpcState.openInitial(state);

            assert initialAck <= initialMax;

            delegate.doAppWindow(traceId, authorization, budgetId, padding);
        }

        private void cleanupNet(
            long traceId,
            long authorization)
        {
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);

            delegate.cleanupApp(traceId, authorization);
        }
    }

    private MessageConsumer newHttpStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        String path,
        String16FW scheme,
        String16FW authority,
        Array32FW<GrpcMetadataFW> metadata)
    {
        final HttpBeginExFW httpBeginEx = httpBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
            .typeId(httpTypeId)
            .headers(hs ->
            {
                hs.item(h -> h
                    .name(HTTP_HEADER_METHOD)
                    .value(HTTP_HEADER_METHOD_POST));
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
                    .name(HTTP_HEADER_CONTENT_TYPE)
                    .value(HEADER_VALUE_CONTENT_TYPE_GRPC));

                metadata.forEach(m -> hs.item(h -> h
                    .name(m.name().value(), 0, m.nameLen())
                    .value(m.value().value(), 0, m.valueLen())));
            }).build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int flags,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int length,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
    }

    private void doEnd(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
        long routeId,
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
            .routeId(routeId)
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
        long routeId,
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
            .routeId(routeId)
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

    private void doReset(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
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
