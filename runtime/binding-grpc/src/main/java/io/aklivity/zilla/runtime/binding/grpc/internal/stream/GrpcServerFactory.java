/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcMethodResult;
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
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcResetExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpEndExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpResetExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;


public final class GrpcServerFactory implements GrpcStreamFactory
{
    private static final int GRPC_MESSAGE_PADDING = 5;
    private static final int DATA_FLAG_INIT = 0x02;
    private static final int DATA_FLAG_CONT = 0x00;
    private static final int DATA_FLAG_FIN = 0x01;
    private static final int EXPIRING_SIGNAL = 1;
    private static final String HTTP_TYPE_NAME = "http";
    private static final byte HYPHEN_BYTE = '-';
    private static final byte COMMA_BYTE = ',';
    private static final byte SPACE_BYTE = ' ';
    private static final byte[] COLON_SPACE_BYTES = ": ".getBytes(US_ASCII);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(US_ASCII);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");
    private static final String8FW HEADER_NAME_GRPC_ENCODING = new String8FW("grpc-encoding");
    private static final String8FW HEADER_NAME_GRPC_STATUS = new String8FW("grpc-status");
    private static final String8FW HEADER_NAME_GRPC_MESSAGE = new String8FW("grpc-message");
    private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
    private static final String8FW HEADER_NAME_STATUS = new String8FW(":status");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_GRPC = new String16FW("application/grpc");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_GRPC_PROTO = new String16FW("application/grpc+proto");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_GRPC_WEB_PROTO = new String16FW("application/grpc-web+proto");
    private static final String16FW HEADER_VALUE_TRAILERS = new String16FW("trailers");
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
    private static final OctetsFW DASH_BIN_OCTETS = new OctetsFW().wrap(new String16FW("-bind").value(), 0, 4);

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
    private final MutableInteger headerOffsetRW = new MutableInteger();
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final GrpcDataExFW grpcDataExRO = new GrpcDataExFW();
    private final GrpcResetExFW resetExRO = new GrpcResetExFW();
    private final GrpcAbortExFW abortExRO = new GrpcAbortExFW();
    private final GrpcMessageFW grpcMessageRO = new GrpcMessageFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final HttpEndExFW.Builder httpEndExRW = new HttpEndExFW.Builder();
    private final HttpResetExFW.Builder httpResetExRW = new HttpResetExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();
    private final GrpcDataExFW.Builder grpcDataExRW = new GrpcDataExFW.Builder();
    private final GrpcMessageFW.Builder grpcMessageRW = new GrpcMessageFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer metadataBuffer;
    private final BufferPool bufferPool;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongFunction<CatalogHandler> supplyCatalog;
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
                long authorization,
                int reserved)
            {
                server.doGrpcNetStatus(traceId, authorization, reserved, HEADER_VALUE_GRPC_OK, null);
            }

            @Override
            public void doNetAbort(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status,
                String16FW message)
            {
                server.doGrpcNetStatus(traceId, authorization, 0, status, message);
            }

            @Override
            public void doNetReset(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status,
                String16FW message)
            {
                server.doGrpcNetReset(traceId, authorization, status, message);
            }
        },
        GRPC_WEB_PROTO
        {
            @Override
            public void doNetEnd(
                GrpcServer server,
                long traceId,
                long authorization,
                int reserved)
            {
                server.doGrpcWebNetStatus(traceId, authorization, HEADER_VALUE_GRPC_OK, null);
            }

            @Override
            public void doNetAbort(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status,
                String16FW message)
            {
                server.doGrpcWebNetStatus(traceId, authorization, status, message);
            }

            @Override
            public void doNetReset(
                GrpcServer server,
                long traceId,
                long authorization,
                String16FW status,
                String16FW message)
            {
                server.doGrpcWebNetStatus(traceId, authorization, status, message);
            }
        };

        public abstract void doNetEnd(
            GrpcServer server,
            long traceId,
            long authorization,
            int reserved);

        public abstract void doNetAbort(
            GrpcServer server,
            long traceId,
            long authorization,
            String16FW status,
            String16FW message);

        public abstract void doNetReset(
            GrpcServer server,
            long traceId,
            long authorization,
            String16FW status,
            String16FW message);
    }


    public GrpcServerFactory(
        GrpcConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.metadataBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyCatalog = context::supplyCatalog;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.grpcTypeId = context.supplyTypeId(GrpcBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return grpcTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        GrpcBindingConfig grpcBinding = new GrpcBindingConfig(binding, metadataBuffer, supplyCatalog);
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long initialId = begin.streamId();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        @SuppressWarnings("resource")
        MessageConsumer newStream = (t, b, i, l) -> {};

        if (!isGrpcRequestMethod(httpBeginEx))
        {
            doRejectNet(network, originId, routedId, traceId, authorization, initialId, sequence, acknowledge,
                HEADER_VALUE_STATUS_405, HEADER_VALUE_GRPC_INTERNAL_ERROR, null);
        }
        else
        {
            final GrpcBindingConfig binding = bindings.get(routedId);

            final GrpcMethodResult method = binding != null ? binding.resolveMethod(httpBeginEx) : null;
            final ContentType contentType = method != null ? asContentType(method.contentType) : null;

            if (method == null)
            {
                doRejectNet(network, originId, routedId, traceId, authorization, initialId, sequence, acknowledge,
                    HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED, null);
            }
            else if (contentType == null)
            {
                doRejectNet(network, originId, routedId, traceId, authorization, initialId, sequence, acknowledge,
                    HEADER_VALUE_STATUS_415, null, null);
            }
            else if (method.te == null || !HEADER_VALUE_TRAILERS.equals(method.te))
            {
                doRejectNet(network, originId, routedId, traceId, authorization, initialId, sequence, acknowledge,
                    HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_ABORTED, null);
            }
            else if ("grpc.health.v1.Health".equals(method.service))
            {
                newStream = newHealthCheckStream(begin, network, method);
            }
            else
            {
                newStream = newInitialGrpcStream(begin, network, contentType, method);
            }
        }
        return newStream;
    }

    private MessageConsumer newHealthCheckStream(
            final BeginFW begin,
            final MessageConsumer network,
            final GrpcMethodResult method)
    {
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long replyId = supplyReplyId.applyAsLong(initialId);
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();

        GrpcHealthServer healthServer = new GrpcHealthServer(
            network,
            originId,
            routedId,
            initialId,
            replyId,
            affinity,
            method
        );

        return healthServer::onNetMessage;
    }

    private final class GrpcHealthServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final GrpcMethodResult method;

        private boolean responseSent = false;
        private boolean isCheckRequest = true;
        private boolean serviceKnown = true;   //

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private long replyBud;
        private int replyPad;
        private int replyMax;

        private int state;
        private GrpcHealthServer(
                MessageConsumer network,
                long originId,
                long routedId,
                long initialId,
                long replyId,
                long affinity,
                GrpcMethodResult method)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.method = method;
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

            // mark initial state
            state = GrpcState.openingInitial(state);

            // health.proto only defines "Check" and "Watch"
            if ("Check".equals(method.method))
            {
                isCheckRequest = true;
            }
            else
            {
                isCheckRequest = false; // unsupported streaming for now
            }


            // Ensure we advertise a non-zero receive window so the client can send DATA
            if (this.initialMax == 0)
            {
                // Use writeBuffer.capacity() if available, or a default
                this.initialMax = 64 * 1024; // 64 KB default
            }


            // send reply BEGIN back to the network
            doNetBegin(traceId, authorization, affinity);

            // send WINDOW so client can start sending DATA
            final long budgetId = 0L;
            final int padding = 0;
            final int capabilities = 0;
            doNetWindow(traceId, authorization, budgetId, padding, capabilities);
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

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;
            assert initialSeq <= initialAck + initialMax;

            final DirectBuffer buffer = payload.buffer();
            final int offset = payload.offset();
            final int limit = payload.limit();
            final int size = payload.sizeof();
            GrpcBindingConfig binding = bindings.get(routedId);

            if (offset < limit) // check if there is data
            {
                // Decode service name from request body
                final String serviceName = decodeHealthCheckRequest(buffer, offset, limit);

                // Check if this service is known
                this.serviceKnown = binding.grpcServices.stream()
                    .flatMap(p -> p.services.stream())
                    .anyMatch(s -> s.service.equals(serviceName));

                // Encode HealthCheckResponse
                final byte status = (byte) (serviceKnown ? 1 : 3); // 1 = SERVING, 3 = SERVICE_UNKNOWN
                final byte[] grpcResponse = new byte[] { 0x08, status };

                // Send response
                final MutableDirectBuffer replyBuffer = new UnsafeBuffer(new byte[grpcResponse.length]);
                replyBuffer.putBytes(0, grpcResponse);

                doNetData(traceId, authorization, budgetId, reserved,
                    DATA_FLAG_INIT | DATA_FLAG_FIN,
                    replyBuffer, 0, grpcResponse.length);

                // End the stream (health check doesn’t need to delegate or route)
                doNetEnd(traceId, authorization, reserved);
            }

        }



        private String decodeHealthCheckRequest(DirectBuffer buffer, int offset, int limit)
        {
            // Skip the 5-byte gRPC frame header (1-byte flag + 4-byte length)
            int pos = offset + 5;

            if (pos >= limit)
            {
                return ""; // empty request
            }

            // Read protobuf key (should be 0x0A)
            int key = buffer.getByte(pos++) & 0xFF;
            if (key != 0x0A)
            {
                return ""; // not the expected field
            }

            // Decode length (varint)
            int length = 0;
            int shift = 0;
            int b;
            do
            {
                b = buffer.getByte(pos++) & 0xFF;
                length |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            if (pos + length > limit)
            {
                return ""; // malformed
            }

            byte[] strBytes = new byte[length];
            buffer.getBytes(pos, strBytes, 0, length);

            return new String(strBytes, StandardCharsets.UTF_8);
        }



        private void onNetEnd(
                EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final int reserved = end.reserved();
            // sanity checks
            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closeReply(state);
                doNetEnd(traceId, authorization, reserved);
            }

            assert initialAck <= initialSeq;
        }


        private void onNetReset(
                ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final int maximum = reset.maximum();

            // sanity checks
            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;

            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closeReply(state);
                doNetReset(traceId, authorization);
            }

            assert replyAck <= replySeq;
        }

        private void onNetAbort(
                AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            // sanity checks
            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);
                doNetAbort(traceId, authorization);
            }

            assert initialAck <= initialSeq;
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
            replyPad = padding;
            replyBud = budgetId;

            state = GrpcState.openReply(state);

            if (!responseSent && replyMax > 0  && isCheckRequest)
            {
                if (serviceKnown)
                {
                    // Known service → send SERVING response
                    final byte[] protoPayload = encodeHealthCheckResponse(1); // 1 = SERVING

                    // Wrap with gRPC message framing (5-byte prefix)
                    final int messageLength = protoPayload.length;
                    final byte[] grpcFrame = new byte[5 + messageLength];
                    grpcFrame[0] = 0; // compressed flag = 0
                    grpcFrame[1] = (byte) ((messageLength >> 24) & 0xFF);
                    grpcFrame[2] = (byte) ((messageLength >> 16) & 0xFF);
                    grpcFrame[3] = (byte) ((messageLength >> 8) & 0xFF);
                    grpcFrame[4] = (byte) (messageLength & 0xFF);
                    System.arraycopy(protoPayload, 0, grpcFrame, 5, messageLength);

                    final MutableDirectBuffer response = new UnsafeBuffer(grpcFrame);
                    int reserved = grpcFrame.length + replyPad;

                    doNetData(traceId, authorization, budgetId, reserved,
                        DATA_FLAG_INIT | DATA_FLAG_FIN, response, 0, grpcFrame.length);
                }
                else
                {
                    // Unknown service → send grpc-status = NOT_FOUND (5) in trailers
                    final byte[] trailers = encodeGrpcTrailers("grpc-status", "5", "grpc-message", "SERVICE_UNKNOWN");
                    int reserved = trailers.length + replyPad;

                    doNetData(traceId, authorization, budgetId, reserved,
                              DATA_FLAG_FIN, new UnsafeBuffer(trailers), 0, trailers.length);
                }

                responseSent = true;

                if (isCheckRequest)
                {
                    doNetEnd(traceId, authorization, 0);
                }
            }
        }

        private byte[] encodeGrpcTrailers(String... keyValues)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keyValues.length; i += 2)
            {
                sb.append(keyValues[i]).append(": ").append(keyValues[i + 1]).append("\r\n");
            }
            return sb.toString().getBytes(StandardCharsets.US_ASCII);
        }

        private byte[] encodeHealthCheckResponse(int status)
        {
            // HealthCheckResponse has a single enum field: status = 1
            // Protobuf encoding: field 1 (enum) = (1 << 3) | 0 = 0x08
            int fieldKey = 0x08;

            // Now build protobuf: [fieldKey][varint(status)]
            // Worst case: 1 byte for key + up to 5 bytes for varint
            byte[] buffer = new byte[6];
            int offset = 0;

            // Write field key
            buffer[offset++] = (byte) fieldKey;

            // Write varint for status
            int value = status;
            while ((value & ~0x7F) != 0)
            {
                buffer[offset++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            buffer[offset++] = (byte) value;

            // Trim to actual size used
            byte[] result = new byte[offset];
            System.arraycopy(buffer, 0, result, 0, offset);

            return result;
        }


        private void doNetBegin(long traceId, long authorization, long affinity)
        {
            BeginFW begin = beginRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .affinity(affinity)
                    .build();

            network.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            state = GrpcState.openingReply(state);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = this.initialAck;
            initialMax = this.initialMax;

            doWindow(
                network,
                originId,
                routedId,
                initialId,
                initialSeq,
                initialAck,
                initialMax,
                traceId,
                authorization,
                budgetId,     // from WindowFW
                padding,      // from WindowFW
                capabilities  // from WindowFW
            );
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
            DataFW data = dataRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .budgetId(budgetId)
                    .reserved(reserved)
                    .flags(flags)
                    .payload(buffer, offset, length)
                    .build();

            // send on the network reply stream
            network.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            replySeq += length;
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            int reserved)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closingReply(state);
                EndFW end = endRW.wrap(extBuffer, 0, extBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .reserved(reserved)
                    .build();

                network.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
            }
        }


        private void doNetReset(
            long traceId,
            long authorization)
        {

            state = GrpcState.closingInitial(state);

        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            // Mark the stream as closing due to abort
            state = GrpcState.closingInitial(state);

            // TODO: DO I need to Send clean up the expiring data?
        }

    }

    private MessageConsumer  newInitialGrpcStream(
        final BeginFW begin,
        final MessageConsumer network,
        final ContentType contentType,
        final GrpcMethodResult method)
    {
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long replyId = supplyReplyId.applyAsLong(initialId);
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();
        final long authorization = begin.authorization();
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();

        GrpcBindingConfig binding = bindings.get(routedId);

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
                originId,
                routedId,
                initialId,
                replyId,
                affinity,
                route.id,
                contentType,
                method)::onNetMessage;
        }
        else
        {
            doRejectNet(network, originId, routedId, traceId, authorization, initialId, sequence, acknowledge,
                HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED, null);
            newStream = (t, b, i, l) -> {};
        }

        return newStream;
    }

    private final class GrpcServer
    {
        private final MessageConsumer network;
        private final GrpcStream delegate;
        private final ContentType contentType;
        private final GrpcMethodResult method;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private String16FW status;
        private String16FW message;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private long replyBud;
        private int replyPad;
        private int replyMax;

        private int state;
        private int messageDeferred;
        private long expiringId = NO_CANCEL_ID;

        private GrpcServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long replyId,
            long affinity,
            long resolveId,
            ContentType contentType,
            GrpcMethodResult method)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.contentType = contentType;
            this.method = method;
            this.delegate = new GrpcStream(routedId, resolveId, this);
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
                expiringId = signaler.signalAt(now().toEpochMilli() + grpcTimeout,
                        originId, routedId, replyId, traceId, EXPIRING_SIGNAL, 0);
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
                final GrpcMessageFW grpcMessage = grpcMessageRO.tryWrap(buffer, offset, limit);
                if (grpcMessage != null)
                {
                    final int messageLength = grpcMessage.length();
                    final int payloadSize = size - GRPC_MESSAGE_PADDING;
                    messageDeferred = messageLength - payloadSize;

                    Flyweight dataEx = messageDeferred > 0 ?
                        grpcDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(grpcTypeId)
                            .deferred(messageDeferred)
                            .build() : EMPTY_OCTETS;

                    int flags = messageDeferred > 0 ? DATA_FLAG_INIT : DATA_FLAG_INIT | DATA_FLAG_FIN;
                    delegate.doAppData(traceId, authorization, budgetId, reserved, flags,
                        buffer, offset + GRPC_MESSAGE_PADDING, payloadSize, dataEx);
                }
                else
                {
                    doNetReset(traceId, authorization, HEADER_VALUE_GRPC_INTERNAL_ERROR, null);
                }
            }
            else
            {
                messageDeferred -= size;
                assert messageDeferred >= 0;

                int flags = messageDeferred > 0 ? DATA_FLAG_CONT : DATA_FLAG_FIN;

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
            replyPad = padding;
            replyBud = budgetId;

            state = GrpcState.openReply(state);

            assert replyAck <= replySeq;

            if (GrpcState.initialClosing(state) && status != null)
            {
                doGrpcWebNetStatus(traceId, authorization, status, message);
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
            long authorization = signal.authorization();
            int signalId = signal.signalId();

            switch (signalId)
            {
            case EXPIRING_SIGNAL:
                onStreamExpiring(traceId, authorization);
                break;
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long replySeq,
            long replyAck,
            int replyMax,
            Array32FW<GrpcMetadataFW> metadata)
        {
            this.replySeq = replySeq;

            doBegin(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                affinity, hs ->
                {
                    hs.item(h -> h.name(HEADER_NAME_STATUS).value(HEADER_VALUE_STATUS_200))
                        .item(h -> h.name(HEADER_NAME_CONTENT_TYPE).value(method.contentType))
                        .item(h -> h.name(HEADER_NAME_GRPC_ENCODING).value(HEADER_VALUE_GRPC_ENCODING));

                    headerOffsetRW.value = 0;

                    if (metadata != null)
                    {
                        metadata.forEach(m ->
                        {
                            if (m.type().get() == GrpcType.BASE64)
                            {
                                OctetsFW name = octetsRW.wrap(extBuffer, headerOffsetRW.value, extBuffer.capacity())
                                    .put(m.name()).put(DASH_BIN_OCTETS).build();
                                headerOffsetRW.value += octetsRW.limit();

                                hs.item(h -> h
                                    .name(name.value(), 0, name.sizeof())
                                    .value(m.value().value(), 0, m.valueLen()));
                            }
                            else
                            {
                                hs.item(h -> h
                                    .name(m.name().value(), 0, m.nameLen())
                                    .value(m.value().value(), 0, m.valueLen()));
                            }
                        });
                    }
                });

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
            doData(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length);

            replySeq += reserved;

            assert replySeq <= replyAck + replyMax;
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            int reserved)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closingReply(state);

                contentType.doNetEnd(this, traceId, authorization, reserved);

                cleanupExpiringIfNecessary();
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            String16FW status,
            String16FW message)
        {
            if (!GrpcState.replyClosed(state))
            {
                state = GrpcState.closingReply(state);
                contentType.doNetAbort(this, traceId, authorization, status, message);

                cleanupExpiringIfNecessary();
            }
        }

        private void doNetReset(
            long traceId,
            long authorization,
            String16FW status,
            String16FW message)
        {

            state = GrpcState.closingInitial(state);
            contentType.doNetReset(this, traceId, authorization, status, message);

            cleanupExpiringIfNecessary();
        }

        private void doNetWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = delegate.initialAck;
            initialMax = delegate.initialMax;

            doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private void doGrpcNetReset(
            long traceId,
            long authorization,
            String16FW status,
            String16FW message)
        {
            if (!GrpcState.initialClosed(state))
            {
                doRejectNet(network, originId, routedId, traceId, authorization,  initialId, initialSeq, initialAck,
                    HEADER_VALUE_STATUS_200, status, message);

                state = GrpcState.closeInitial(state);
            }
        }

        private void doGrpcNetStatus(
            long traceId,
            long authorization,
            int reserved,
            String16FW status,
            String16FW message)
        {
            if (!GrpcState.replyClosed(state))
            {
                HttpEndExFW.Builder trailer = httpEndExRW.wrap(writeBuffer, EndFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
                    .typeId(httpTypeId)
                    .trailersItem(t -> t.name(HEADER_NAME_GRPC_STATUS).value(status));

                if (message != null)
                {
                    trailer.trailersItem(t -> t.name(HEADER_NAME_GRPC_MESSAGE).value(message));
                }

                doEnd(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBud, reserved, trailer.build());

                state = GrpcState.closeReply(state);
            }
        }

        private void doGrpcWebNetStatus(
            long traceId,
            long authorization,
            String16FW status,
            String16FW message)
        {
            if (!GrpcState.replyClosed(state))
            {
                final MutableDirectBuffer encodeBuffer = writeBuffer;
                final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
                final int encodeLimit = encodeBuffer.capacity();
                int encodeProgress = encodeOffset + GRPC_MESSAGE_PADDING;

                encodeProgress = doEncodeHeader(encodeBuffer, encodeProgress,
                    HEADER_NAME_GRPC_STATUS.value(), status.value(), false);

                if (message != null)
                {
                    encodeProgress = doEncodeHeader(encodeBuffer, encodeProgress,
                        HEADER_NAME_GRPC_MESSAGE.value(), message.value(), false);
                }

                final int headersLength = encodeProgress - encodeOffset - GRPC_MESSAGE_PADDING;
                grpcMessageRW.wrap(encodeBuffer, encodeOffset, encodeLimit)
                    .flag(128)
                    .length(headersLength).build();
                final int messageSize = headersLength + GRPC_MESSAGE_PADDING;
                final int reserved = messageSize + replyPad;

                if (replyWindow() >= reserved)
                {
                    doNetData(traceId, authorization, replyBud, reserved, DATA_FLAG_INIT | DATA_FLAG_FIN,
                        encodeBuffer, encodeOffset, messageSize);

                    doEnd(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, replyBud, replyPad, EMPTY_OCTETS);

                    state = GrpcState.closeReply(state);
                }
                else
                {
                    this.status = status;
                    this.message = message;
                }
            }
        }

        private void onStreamExpiring(
            long traceId,
            long authorization)
        {
            expiringId = NO_CANCEL_ID;

            if (GrpcState.replyOpening(state))
            {
                contentType.doNetAbort(this, traceId, authorization, HEADER_VALUE_GRPC_DEADLINE_EXCEEDED, null);
            }
            else
            {
                doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, 0, 0, 0);

                doHttpResponse(network, traceId, authorization, replyBud, affinity, originId,
                    routedId, replyId, replySeq, replyAck, writeBuffer.capacity(), replyPad,
                    HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_DEADLINE_EXCEEDED, null);
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
    }

    private final class GrpcStream
    {
        // The network-side handler for this stream. GrpcStream represents the application-facing
        // side and delegates network operations (BEGIN/DATA/END/RESET etc.) to the GrpcServer instance.
        private final GrpcServer delegate;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer application;
        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private GrpcStream(
            long originId,
            long routedId,
            GrpcServer delegate)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.delegate = delegate;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(this.initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            application = newGrpcStream(this::onAppMessage, originId, routedId, initialId, initialSeq, initialAck,
                initialMax, traceId, authorization, affinity, delegate.method);
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

            doData(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, flags, reserved, buffer, offset, length, extension);

            initialSeq += reserved;

            assert initialSeq <= initialAck + initialMax;
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doEnd(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization,  0L, 0, EMPTY_OCTETS);
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
            this.replyAck = replyAck;
            this.replyMax = replyMax;

            doWindow(application, originId, routedId, replyId, replySeq, this.replyAck, this.replyMax,
                traceId, authorization, budgetId, padding + GRPC_MESSAGE_PADDING, capabilities);
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!GrpcState.initialClosed(state))
            {
                state = GrpcState.closeInitial(state);

                doAbort(application, originId, routedId, initialId, initialSeq, initialAck, initialMax,
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
            final String16FW message = resetEx != null ? resetEx.message() : null;

            assert acknowledge <= sequence;
            assert acknowledge >= initialAck;

            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            delegate.doNetReset(traceId, authorization, status, message);
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
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            state = GrpcState.openInitial(state);

            initialAck = acknowledge;
            initialMax = maximum;

            delegate.doNetWindow(authorization, traceId, budgetId, padding, capabilities);
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            final OctetsFW extension = begin.extension();

            final GrpcBeginExFW grpcBeginEx = extension.get(grpcBeginExRO::tryWrap);

            state = GrpcState.openReply(state);

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge >= replyAck;

            replySeq = sequence;
            replyAck = acknowledge;
            state = GrpcState.openingReply(state);

            delegate.doNetBegin(traceId, authorization, replySeq, replyAck, replyMax,
                grpcBeginEx != null ? grpcBeginEx.metadata() : null);
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
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

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
            final int reserved = end.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcState.closeReply(state);

            delegate.doNetEnd(traceId, authorization, reserved);
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
            final String16FW message = abortEx != null ? abortEx.message() : null;

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = GrpcState.closeReply(state);

            assert replyAck <= replySeq;

            delegate.doNetAbort(traceId, authorization, status, message);
        }


        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!GrpcState.replyClosed(state))
            {
                doReset(application, originId, routedId, replyId, replySeq, replyAck, replyMax,
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
        long originId,
        long routedId,
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
            .originId(originId)
            .routedId(routedId)
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
        DirectBuffer payload,
        int offset,
        int length)
    {
        doData(receiver, originId, routedId, streamId, sequence, acknowledge, maximum, traceId, authorization, budgetId, flags,
            reserved, payload, offset, length, EMPTY_OCTETS);
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
        DirectBuffer payload,
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
        long budgetId,
        int reserved,
        Flyweight extension)
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
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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

    private void doRejectNet(
        MessageConsumer network,
        long originId,
        long routedId,
        long traceId,
        long authorization,
        long initialId,
        long sequence,
        long acknowledge,
        String16FW httpStatus,
        String16FW grpcStatus,
        String16FW grpcMessage)
    {
        doWindow(network, originId, routedId, initialId, sequence, acknowledge, 0, traceId, 0L, 0, 0, 0);
        HttpResetExFW.Builder resetEx = httpResetExRW.wrap(extBuffer, 0, extBuffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(HEADER_NAME_STATUS).value(httpStatus));
        if (grpcStatus != null)
        {
            resetEx.headersItem(h -> h.name(HEADER_NAME_GRPC_STATUS).value(grpcStatus));
        }

        if (grpcMessage != null && grpcMessage.value() != null)
        {
            resetEx.headersItem(h -> h.name(HEADER_NAME_GRPC_MESSAGE).value(grpcMessage));
        }

        doReset(network, originId, routedId, initialId, sequence, acknowledge, 0, traceId, authorization, resetEx.build());
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
        long budgetId,
        long affinity,
        long originId,
        long routedId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        int padding,
        String16FW httpStatus,
        String16FW grpcStatus,
        String16FW grpcMessage)
    {
        doBegin(acceptReply, originId, routedId, replyId, sequence, acknowledge, maximum, traceId,
            authorization, affinity, hs ->
            {
                hs.item(h -> h.name(HEADER_NAME_STATUS).value(httpStatus))
                    .item(h -> h.name(HEADER_NAME_GRPC_STATUS).value(grpcStatus));

                if (grpcMessage != null)
                {
                    hs.item(h -> h.name(HEADER_NAME_GRPC_MESSAGE).value(grpcMessage));
                }
            });
        doEnd(acceptReply, originId, routedId, replyId, sequence, acknowledge, maximum,
            traceId, authorization, budgetId, padding, EMPTY_OCTETS);
    }

    private MessageConsumer newGrpcStream(
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
        GrpcMethodResult method)
    {
        final GrpcBeginExFW grpcBegin = grpcBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
            .typeId(grpcTypeId)
            .scheme(method.scheme)
            .authority(method.authority)
            .service(method.service.toString())
            .method(method.method.toString())
            .metadata(method.metadata)
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
        String16FW value)
    {
        ContentType type = null;

        if (HEADER_VALUE_CONTENT_TYPE_GRPC.value().compareTo(value.value()) == 0 ||
            HEADER_VALUE_CONTENT_TYPE_GRPC_PROTO.value().compareTo(value.value()) == 0)
        {
            type = GRPC;
        }
        else if (HEADER_VALUE_CONTENT_TYPE_GRPC_WEB_PROTO.value().compareTo(value.value()) == 0)
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
