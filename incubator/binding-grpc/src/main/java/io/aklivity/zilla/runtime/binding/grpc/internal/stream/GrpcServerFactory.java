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

import static io.aklivity.zilla.runtime.binding.grpc.internal.stream.GrpcServerFactory.ContentType.GRPC;
import static io.aklivity.zilla.runtime.binding.grpc.internal.stream.GrpcServerFactory.ContentType.GRPC_WEB_PROTO;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Instant.now;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcMethodResolver;
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
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcAbortExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcDataExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class GrpcServerFactory implements GrpcStreamFactory
{
    private static final int GRPC_MESSAGE_PADDING = 5;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int EXPIRING_SIGNAL = 1;
    private static final String HTTP_TYPE_NAME = "http";
    private static final String APPLICATION_GRPC = "application/grpc";
    private static final String APPLICATION_GRPC_PROTO = "application/grpc+proto";
    private static final String APPLICATION_GRPC_WEB_PROTO = "application/grpc-web+proto";
    private static final byte HYPHEN_BYTE = '-';
    private static final byte COMMA_BYTE = ',';
    private static final byte SPACE_BYTE = ' ';
    private static final byte[] COLON_SPACE_BYTES = ": ".getBytes(US_ASCII);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(US_ASCII);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");
    private static final String8FW HEADER_NAME_GRPC_ENCODING = new String8FW("grpc-encoding");
    private static final String8FW HEADER_NAME_GRPC_STATUS = new String8FW("grpc-status");
    private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
    private static final String8FW HEADER_NAME_STATUS = new String8FW(":status");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_GRPC = new String16FW("application/grpc");
    private static final String16FW HEADER_VALUE_GRPC_ENCODING = new String16FW("identity");
    private static final String16FW HEADER_VALUE_METHOD_POST = new String16FW("POST");
    private static final String16FW HEADER_VALUE_STATUS_200 = new String16FW("200");
    private static final String16FW HEADER_VALUE_STATUS_405 = new String16FW("405");
    private static final String16FW HEADER_VALUE_STATUS_415 = new String16FW("415");
    private static final String16FW HEADER_VALUE_GRPC_OK = new String16FW("0");
    private static final String16FW HEADER_VALUE_GRPC_DEADLINE_EXCEEDED = new String16FW("4");
    private static final String16FW HEADER_VALUE_GRPC_ABORTED = new String16FW("10");
    private static final String16FW HEADER_VALUE_GRPC_UNIMPLEMENTED = new String16FW("12");
    private static final String16FW HEADER_VALUE_GRPC_INTERNAL_ERROR = new String16FW("13");

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final GrpcResetExFW resetExRO = new GrpcResetExFW();
    private final GrpcAbortExFW abortExRO = new GrpcAbortExFW();
    private final GrpcMessageFW grpcMessageRO = new GrpcMessageFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpEndExFW.Builder httpEndExRW = new HttpEndExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcMessageFW.Builder grpcMessageRW = new GrpcMessageFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer metadataBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final Long2ObjectHashMap<GrpcBindingConfig> bindings;
    private final int grpcTypeId;
    private final int httpTypeId;


    enum ContentType
    {
        GRPC
        {
            @Override
            public void doNetEnd(
                GrpcServer server,
                long traceId,
                long authorization)
            {
                server.doGrpcNetStatus(traceId, authorization, HEADER_VALUE_GRPC_OK);
            }

            @Override
            public void doNetAbort(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status)
            {
                server.doGrpcNetStatus(traceId, authorization, status);
            }

            @Override
            public void doNetReset(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status)
            {
                server.doGrpcNetStatus(traceId, authorization, status);
            }
        },
        GRPC_WEB_PROTO
        {
            @Override
            public void doNetEnd(
                GrpcServer server,
                long traceId,
                long authorization)
            {
                server.doGrpcWebNetStatus(traceId, authorization, HEADER_VALUE_GRPC_OK);
            }

            @Override
            public void doNetAbort(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status)
            {
                server.doGrpcWebNetStatus(traceId, authorization, status);
            }

            @Override
            public void doNetReset(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status)
            {
                server.doGrpcWebNetStatus(traceId, authorization, status);
            }
        };

        public abstract void doNetEnd(
            GrpcServer server,
            long traceId,
            long authorization);

        public abstract void doNetAbort(
            GrpcServer server,
            long traceId,
            long authorization,
            String16FW status);

        public abstract void doNetReset(
            GrpcServer server,
            long traceId,
            long authorization,
            String16FW status);
    }

    public GrpcServerFactory(
        GrpcConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.metadataBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.grpcTypeId = context.supplyTypeId(GrpcBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();
        final long initialId = begin.streamId();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        MessageConsumer newStream = null;

        if (!isGrpcRequestMethod(httpBeginEx))
        {
            doHttpResponse(network, traceId, authorization, affinity, routeId, initialId, sequence, acknowledge,
                HEADER_VALUE_STATUS_405, HEADER_VALUE_GRPC_INTERNAL_ERROR);
            newStream = (t, b, i, l) -> {};
        }
        else
        {
            final GrpcBindingConfig binding = bindings.get(routeId);

            final GrpcMethodResolver method = binding != null ? binding.resolveMethod(httpBeginEx) : null;
            final ContentType contentType = asContentType(method.contentType);

            if (contentType == null)
            {
                doHttpResponse(network, traceId, authorization, affinity, routeId, initialId, sequence, acknowledge,
                    HEADER_VALUE_STATUS_415, HEADER_VALUE_GRPC_ABORTED);
            }
            else if (method != null)
            {
                newStream = newInitialGrpcStream(begin, network, contentType, method);
            }
            else
            {
                doHttpResponse(network, traceId, authorization, affinity, routeId, initialId, sequence, acknowledge,
                    HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED);
                newStream = (t, b, i, l) -> {};
            }
        }

        return newStream;
    }

    private MessageConsumer  newInitialGrpcStream(
        final BeginFW begin,
        final MessageConsumer network,
        final ContentType contentType,
        final GrpcMethodResolver method)
    {
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long replyId = supplyReplyId.applyAsLong(initialId);
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();

        GrpcBindingConfig binding = bindings.get(routeId);

        GrpcRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(begin.authorization(), method.service, method.method, method.metadata);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {
            newStream = new GrpcServer(
                network,
                routeId,
                initialId,
                replyId,
                affinity,
                route.id,
                contentType,
                method)::onNetMessage;
        }
        else
        {
            doHttpResponse(network, traceId, authorization, affinity, routeId, initialId, sequence, acknowledge,
                HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED);

            newStream = (t, b, i, l) -> {};
        }

        return newStream;
    }

    private final class GrpcServer
    {
        private final MessageConsumer network;
        private final GrpcStream delegate;
        private final ContentType contentType;
        private final GrpcMethodResolver method;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private String16FW status;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long replySeq;
        private long replyAck;
        private int replyPadding;
        private int replyMax;
        private int state;
        private int messageDeferred;
        private long expiringId = NO_CANCEL_ID;

        private GrpcServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long replyId,
            long affinity,
            long resolveId,
            ContentType contentType,
            GrpcMethodResolver method)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.contentType = contentType;
            this.method = method;

            this.delegate = new GrpcStream(resolveId, this);
        }

        private int replyWindow()
        {
            return replyMax - (int)(replySeq - replyAck);
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetSignal(signal);
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
            final long affinity = begin.affinity();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            state = GrpcState.openingInitial(state);

            delegate.doAppBegin(traceId, authorization, affinity);

            final long grpcTimeout = method.grpcTimeout;
            if (grpcTimeout > 0L)
            {
                expiringId = signaler.signalAt(now().toEpochMilli() + grpcTimeout, routeId, initialId, EXPIRING_SIGNAL, 0);
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
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;
            assert initialSeq <= initialAck + initialMax;

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

        private void onNetEnd(
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
            assert sequence >= initialSeq;

            initialSeq = sequence;
            state = GrpcState.closeInitial(state);

            assert initialAck <= initialSeq;

            delegate.doAppAbort(traceId, authorization);
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
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyPadding = padding;
            state = GrpcState.openReply(state);

            assert replyAck <= replySeq;

            if (GrpcState.initialClosing(state) && status != null)
            {
                doGrpcWebNetStatus(traceId, authorization, status);
            }
            else
            {
                delegate.doAppWindow(traceId, authorization, budgetId, padding, capabilities, replyAck, replyMax);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            state = GrpcState.closeReply(state);

            assert replyAck <= replySeq;
            delegate.doAppReset(traceId, authorization);
        }

        private void onNetSignal(
            SignalFW signal)
        {
            long traceId = signal.traceId();
            int signalId = signal.signalId();

            switch (signalId)
            {
            case EXPIRING_SIGNAL:
                onStreamExpiring(traceId);
                break;
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long replySeq,
            long replyAck,
            int replyMax)
        {
            this.replySeq = replySeq;
            this.replyAck = replyAck;
            this.replyMax = replyMax;

            doBegin(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                affinity, hs -> hs.item(h -> h.name(HEADER_NAME_STATUS).value(HEADER_VALUE_STATUS_200))
                    .item(h -> h.name(HEADER_NAME_CONTENT_TYPE).value(HEADER_VALUE_CONTENT_TYPE_GRPC))
                    .item(h -> h.name(HEADER_NAME_GRPC_ENCODING).value(HEADER_VALUE_GRPC_ENCODING)));

            state = GrpcState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            int flags,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            doData(network, routeId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closingReply(state);

                contentType.doNetEnd(this, traceId, authorization);

                cleanupExpiringIfNecessary();
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closingReply(state);

                contentType.doNetAbort(this, traceId, authorization, status);
                cleanupExpiringIfNecessary();
            }
        }

        private void doNetReset(
            long traceId,
            long authorization,
            String16FW status)
        {

            if (GrpcState.initialOpening(state))
            {
                doHttpResponse(network, traceId, authorization, affinity, routeId, initialId, initialSeq, initialAck,
                    HEADER_VALUE_STATUS_200, status);
            }
            else
            {
                contentType.doNetReset(this, traceId, authorization, status);
            }
            state = GrpcState.closingReply(state);

            cleanupExpiringIfNecessary();

        }

        private void doNetWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.grpcInitialAck;
            initialMax = delegate.grpcInitialMax;

            doWindow(network, routeId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcNetStatus(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (!GrpcState.replyClosed(state))
            {
                HttpEndExFW trailer = httpEndExRW.wrap(writeBuffer, EndFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(httpTypeId)
                    .trailersItem(t -> t.name(HEADER_NAME_GRPC_STATUS).value(status))
                    .build();

                doEnd(network, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, trailer);

                state = GrpcState.closeReply(state);
            }
        }

        private void doGrpcWebNetStatus(
            long traceId,
            long authorization,
            String16FW status)
        {
            if (!GrpcState.replyClosed(state))
            {
                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int encodeLimit = encodeBuffer.capacity();
                int encodeProgress = encodeOffset;

                GrpcMessageFW grpcMessage = grpcMessageRW.wrap(encodeBuffer, encodeOffset, encodeLimit)
                    .flag(1)
                    .build();
                encodeProgress = grpcMessage.limit();
                encodeProgress = doEncodeHeader(encodeBuffer, encodeProgress,
                    HEADER_NAME_GRPC_STATUS.value(), status.value(), false);

                final int length = encodeProgress - encodeOffset;
                final int reserved = length + replyPadding;
                if (replyWindow() >= reserved)
                {
                    doNetData(traceId, authorization, 0, reserved, DATA_FLAG_INIT | DATA_FLAG_FIN,
                        encodeBuffer, encodeOffset, length);

                    doEnd(network, routeId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, EMPTY_OCTETS);

                    state = GrpcState.closeReply(state);
                }
                else
                {
                    this.status = status;
                }
            }
        }

        private void onStreamExpiring(
            long traceId)
        {
            expiringId = NO_CANCEL_ID;

            if (GrpcState.replyOpening(state))
            {
                contentType.doNetAbort(this, traceId, 0L, HEADER_VALUE_GRPC_DEADLINE_EXCEEDED);
            }
            else
            {
                doHttpResponse(network, traceId, 0L, affinity, routeId, initialId, initialSeq, initialAck,
                    HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_DEADLINE_EXCEEDED);
            }
            delegate.cleanup(traceId, 0L);
        }

        private void cleanupExpiringIfNecessary()
        {
            if (expiringId != NO_CANCEL_ID)
            {
                signaler.cancel(expiringId);
                expiringId = NO_CANCEL_ID;
            }
        }

        private void cleanup(
            long traceId)
        {
            doRequestAbortIfNecessary(traceId);
            doResponseResetIfNecessary(traceId);
        }

    }

    private final class GrpcStream
    {
        private final GrpcServer delegate;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer application;
        private int state;

        private long grpcInitialSeq;
        private long grpcInitialAck;
        private int grpcInitialMax;

        private long grpcReplySeq;
        private long grpcReplyAck;
        private int grpcReplyMax;

        private GrpcStream(
            long routeId,
            GrpcServer delegate)
        {
            this.routeId = routeId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(this.initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            application = newGrpcStream(this::onAppMessage, routeId, initialId, grpcInitialSeq, grpcInitialAck,
                grpcInitialMax, traceId, authorization, affinity, delegate.method);
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

            doData(application, routeId, initialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, extension);

            grpcInitialSeq += reserved;

            assert grpcInitialSeq <= grpcInitialAck + grpcInitialMax;
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doEnd(application, routeId, initialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities,
            long replyAck,
            int replyMax)
        {
            grpcReplyAck = replyAck;
            grpcReplyMax = replyMax;

            doWindow(application, routeId, replyId, grpcReplySeq, grpcReplyAck, grpcReplyMax,
                traceId, authorization, budgetId, padding + GRPC_MESSAGE_PADDING, capabilities);
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doAbort(application, routeId, initialId, grpcInitialSeq, grpcInitialAck, grpcInitialMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
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
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAppAbort(abort);
                break;
            }
        }

        private void onAppReset(
            ResetFW reset)
        {

            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final OctetsFW extension = reset.extension();
            final GrpcResetExFW resetEx = extension.get(resetExRO::tryWrap);

            final String16FW status = resetEx != null ? resetEx.status() : HEADER_VALUE_GRPC_INTERNAL_ERROR;

            assert acknowledge <= sequence;
            assert acknowledge >= grpcInitialAck;

            grpcInitialAck = acknowledge;

            assert grpcInitialAck <= grpcInitialSeq;

            delegate.doNetReset(traceId, authorization, status);
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final long authorization = window.authorization();
            final int maximum = window.maximum();
            final int padding = window.padding();
            final int capabilities = window.capabilities();

            assert acknowledge <= sequence;
            assert sequence <= grpcInitialSeq;
            assert acknowledge >= grpcInitialAck;
            assert maximum >= grpcInitialMax;

            state = GrpcState.openInitial(state);

            grpcInitialAck = acknowledge;
            grpcInitialMax = maximum;

            delegate.doNetWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            state = GrpcState.openReply(state);

            assert acknowledge <= sequence;
            assert sequence >= grpcReplySeq;
            assert acknowledge >= grpcReplyAck;

            grpcReplySeq = sequence;
            grpcReplyAck = acknowledge;
            state = GrpcState.openingReply(state);

            delegate.doNetBegin(traceId, authorization, grpcReplySeq, grpcReplyAck, grpcReplyMax);
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

            assert acknowledge <= sequence;
            assert sequence >= grpcReplySeq;
            assert acknowledge <= grpcReplyAck;

            grpcReplySeq = sequence + reserved;

            assert grpcReplyAck <= grpcReplySeq;

            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();
            final int payloadSize = payload.sizeof();

            int encodeProgress = encodeOffset;

            if ((flags & DATA_FLAG_INIT) != 0x00)
            {
                final GrpcDataExFW grpcDataEx = extension.get(grpcDataExRO::tryWrap);
                final int deferred = grpcDataEx != null ? grpcDataEx.deferred() : 0;
                GrpcMessageFW message = grpcMessageRW
                    .wrap(encodeBuffer, encodeOffset, encodeLimit)
                    .flag(0)
                    .length(payloadSize + deferred)
                    .build();
                encodeProgress = message.limit();
            }

            encodeBuffer.putBytes(encodeProgress, payload.buffer(), payload.offset(), payloadSize);
            encodeProgress += payloadSize;

            delegate.doNetData(traceId, authorization, budgetId, reserved, flags, encodeBuffer, encodeOffset,
                encodeProgress - encodeOffset);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            assert acknowledge <= sequence;
            assert sequence >= grpcReplySeq;

            grpcReplySeq = sequence;
            state = GrpcState.closeReply(state);

            delegate.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final OctetsFW extension = abort.extension();
            final GrpcAbortExFW abortEx = extension.get(abortExRO::tryWrap);

            final String16FW status = abortEx != null ? abortEx.status() : HEADER_VALUE_GRPC_ABORTED;

            assert acknowledge <= sequence;
            assert sequence >= grpcReplySeq;

            grpcReplySeq = sequence;
            state = GrpcState.closeReply(state);

            assert grpcReplyAck <= grpcReplySeq;

            delegate.doNetAbort(traceId, authorization, status);
        }


        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                doReset(application, routeId, replyId, grpcReplySeq, grpcReplyAck, grpcReplyMax,
                    traceId, authorization, EMPTY_OCTETS);
            }
        }

        private void cleanup(
            long traceId,
            long authoritation)
        {
            doAppAbort(traceId, authoritation);
            doAppReset(traceId, authoritation);
        }
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
        long affinity,
        Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> mutator)
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
            .extension(e -> e.set(visitHttpBeginEx(mutator)))
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
        DirectBuffer payload,
        int offset,
        int length)
    {
        doData(receiver, routeId, streamId, sequence, acknowledge, maximum, traceId, authorization, budgetId, flags,
            reserved, payload, offset, length, EMPTY_OCTETS);
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
        DirectBuffer payload,
        int offset,
        int length,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .payload(payload, offset, length)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long routeId,
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
            .routeId(routeId)
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

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity()).routeId(routeId)
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


    private Flyweight.Builder.Visitor visitHttpBeginEx(
        Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> headers)
    {
        return (buffer, offset, limit) ->
            httpBeginExRW.wrap(buffer, offset, limit)
                .typeId(httpTypeId)
                .headers(headers)
                .build()
                .sizeof();
    }

    private void doHttpResponse(
        MessageConsumer acceptReply,
        long traceId,
        long authorization,
        long affinity,
        long routeId,
        long initialId,
        long sequence,
        long acknowledge,
        String16FW httpStatus,
        String16FW grpcStatus)
    {
        final long acceptReplyId = supplyReplyId.applyAsLong(initialId);

        doWindow(acceptReply, routeId, initialId, sequence, acknowledge, 0, traceId, authorization, 0, 0, 0);
        doBegin(acceptReply, routeId, acceptReplyId, 0L, 0L, 0, traceId, authorization, affinity, hs ->
            hs.item(h -> h.name(HEADER_NAME_STATUS).value(httpStatus))
                .item(h -> h.name(HEADER_NAME_GRPC_STATUS).value(grpcStatus)));
        doEnd(acceptReply, routeId, acceptReplyId, 0L, 0L, 0, traceId, 0L, EMPTY_OCTETS);
    }

    private MessageConsumer newGrpcStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        GrpcMethodResolver method)
    {
        final GrpcBeginExFW grpcBegin = grpcBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
            .typeId(grpcTypeId)
            .scheme(method.scheme)
            .authority(method.authority)
            .service(method.service.toString())
            .method(method.method.toString())
            .request(r -> r.set(method.request).build())
            .response(r -> r.set(method.response).build())
            .metadata(method.metadata)
            .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(grpcBegin.buffer(), grpcBegin.offset(), grpcBegin.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private static boolean isGrpcRequestMethod(
        HttpBeginExFW httpBeginEx)
    {
        return httpBeginEx != null &&
            httpBeginEx.headers().anyMatch(h -> HEADER_NAME_METHOD.equals(h.name()) &&
                HEADER_VALUE_METHOD_POST.equals(h.value()));
    }

    private static ContentType asContentType(
        CharSequence value)
    {
        ContentType type = null;

        if (APPLICATION_GRPC.contentEquals(value) || APPLICATION_GRPC_PROTO.contentEquals(value))
        {
            type = GRPC;
        }
        else if (APPLICATION_GRPC_WEB_PROTO.contentEquals(value))
        {
            type = GRPC_WEB_PROTO;
        }
        return type;
    }

    private int doEncodeHeader(
        MutableDirectBuffer buffer,
        int offset,
        DirectBuffer name,
        DirectBuffer value,
        boolean valueInitCaps)
    {
        int progress = offset;
        progress = doEncodeInitialCaps(buffer, name, progress);

        buffer.putBytes(progress, COLON_SPACE_BYTES);
        progress += COLON_SPACE_BYTES.length;

        if (valueInitCaps)
        {
            progress = doEncodeInitialCaps(buffer, value, progress);
        }
        else
        {
            buffer.putBytes(progress, value, 0, value.capacity());
            progress += value.capacity();
        }

        buffer.putBytes(progress, CRLF_BYTES);
        progress += CRLF_BYTES.length;

        return progress;
    }

    private int doEncodeInitialCaps(
        MutableDirectBuffer buffer,
        DirectBuffer name,
        int offset)
    {
        int progress = offset;

        boolean uppercase = true;
        for (int pos = 0, len = name.capacity(); pos < len; pos++, progress++)
        {
            byte ch = name.getByte(pos);
            if (uppercase)
            {
                ch = (byte) toUpperCase(ch);
            }
            else
            {
                ch |= (byte) toLowerCase(ch);
            }
            buffer.putByte(progress, ch);
            uppercase = ch == HYPHEN_BYTE || ch == COMMA_BYTE || ch == SPACE_BYTE;
        }

        return progress;
    }
}
