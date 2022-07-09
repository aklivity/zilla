/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.RequestHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.ResponseHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.sasl.SaslAuthenticateRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.sasl.SaslAuthenticateResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.sasl.SaslHandshakeMechanismResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.sasl.SaslHandshakeRequestFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.sasl.SaslHandshakeResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.EngineContext;

public abstract class KafkaClientSaslHandshaker
{
    private static final short SASL_HANDSHAKE_API_KEY = 17;
    private static final short SASL_HANDSHAKE_API_VERSION = 1;

    private static final short SASL_AUTHENTICATE_API_KEY = 36;
    private static final short SASL_AUTHENTICATE_API_VERSION = 1;

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final SaslHandshakeRequestFW.Builder saslHandshakeRequestRW = new SaslHandshakeRequestFW.Builder();
    private final SaslAuthenticateRequestFW.Builder saslAuthenticateRequestRW = new SaslAuthenticateRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final SaslHandshakeResponseFW saslHandshakeResponseRO = new SaslHandshakeResponseFW();
    private final SaslHandshakeMechanismResponseFW saslHandshakeMechanismResponseRO = new SaslHandshakeMechanismResponseFW();
    private final SaslAuthenticateResponseFW saslAuthenticateResponseRO = new SaslAuthenticateResponseFW();

    protected final LongUnaryOperator supplyInitialId;
    protected final LongUnaryOperator supplyReplyId;
    protected final MutableDirectBuffer writeBuffer;

    public KafkaClientSaslHandshaker(
        EngineContext context)
    {
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
    }

    public abstract class KafkaSaslClient
    {
        protected final KafkaSaslConfig sasl;
        protected final long routeId;
        protected final long initialId;
        protected final long replyId;

        protected int nextRequestId;

        private int decodableResponseBytes;
        private int decodableMechanisms;

        protected KafkaSaslClient(
            KafkaSaslConfig sasl,
            long routeId)
        {
            this.sasl = sasl;
            this.routeId = routeId;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        protected final void doEncodeSaslHandshakeRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .length(0)
                    .apiKey(SASL_HANDSHAKE_API_KEY)
                    .apiVersion(SASL_HANDSHAKE_API_VERSION)
                    .correlationId(0)
                    .clientId((String) null)
                    .build();

            encodeProgress = requestHeader.limit();

            final String mechanism = sasl.mechanism;
            final SaslHandshakeRequestFW saslRequest = saslHandshakeRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .mechanism(mechanism.toUpperCase())
                    .build();

            encodeProgress = saslRequest.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                    .length(requestSize)
                    .apiKey(requestHeader.apiKey())
                    .apiVersion(requestHeader.apiVersion())
                    .correlationId(requestId)
                    .clientId(requestHeader.clientId().asString())
                    .build();

            if (KafkaConfiguration.DEBUG)
            {
                System.out.format("[0x%016x] SASL HANDSHAKE %s\n", replyId, mechanism);
            }

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            doDecodeSaslHandshakeResponse(traceId);
        }

        protected final void doEncodeSaslAuthenticateRequest(
            long traceId,
            long budgetId)
        {
            final MutableDirectBuffer encodeBuffer = writeBuffer;
            final int encodeOffset = DataFW.FIELD_OFFSET_PAYLOAD;
            final int encodeLimit = encodeBuffer.capacity();

            int encodeProgress = encodeOffset;

            final RequestHeaderFW requestHeader = requestHeaderRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                    .length(0)
                    .apiKey(SASL_AUTHENTICATE_API_KEY)
                    .apiVersion(SASL_AUTHENTICATE_API_VERSION)
                    .correlationId(0)
                    .clientId((String) null)
                    .build();

            encodeProgress = requestHeader.limit();

            final String username = sasl.username;
            final String password = sasl.password;
            final SaslAuthenticateRequestFW authenticateRequest =
                    saslAuthenticateRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                        .authBytes(a -> a.put((b, o, l) ->
                        {
                            int p = o;
                            b.putByte(p++, (byte) 0);
                            p += b.putStringWithoutLengthUtf8(p, username);
                            b.putByte(p++, (byte) 0);
                            p += b.putStringWithoutLengthUtf8(p, password);
                            return p - o;
                        }))
                        .build();

            encodeProgress = authenticateRequest.limit();

            final int requestId = nextRequestId++;
            final int requestSize = encodeProgress - encodeOffset - RequestHeaderFW.FIELD_OFFSET_API_KEY;

            requestHeaderRW.wrap(encodeBuffer, requestHeader.offset(), requestHeader.limit())
                    .length(requestSize)
                    .apiKey(requestHeader.apiKey())
                    .apiVersion(requestHeader.apiVersion())
                    .correlationId(requestId)
                    .clientId(requestHeader.clientId().asString())
                    .build();

            if (KafkaConfiguration.DEBUG)
            {
                System.out.format("[0x%016x] SASL AUTHENTICATE %s\n", replyId, username);
            }

            doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

            doDecodeSaslAuthenticateResponse(traceId);
        }

        protected abstract void doNetworkData(
            long traceId,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);

        protected abstract void onDecodeSaslHandshakeResponse(
            long traceId,
            long authorization,
            int errorCode);

        protected abstract void onDecodeSaslAuthenticateResponse(
            long traceId,
            long authorization,
            int errorCode);

        protected abstract void onDecodeSaslResponse(
            long traceId);

        protected abstract void doDecodeSaslHandshakeResponse(
                long traceId);

        protected abstract void doDecodeSaslAuthenticateResponse(
                long traceId);

        protected abstract void doDecodeSaslAuthenticate(
            long traceId);

        protected abstract void doDecodeSaslHandshake(
            long traceId);

        protected abstract void doDecodeSaslHandshakeMechanisms(
            long traceId);

        protected abstract void doDecodeSaslHandshakeMechansim(
            long traceId);
    }

    @FunctionalInterface
    private interface KafkaSaslClientEncoder
    {
        void encode(
            KafkaSaslClient client,
            long replyId,
            long traceId,
            long budgetId);
    }

    @FunctionalInterface
    private interface KafkaSaslClientDecoder
    {
        int decode(
            KafkaSaslClient client,
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }

    protected final int decodeSaslHandshakeResponse(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader != null)
            {
                progress = responseHeader.limit();
                client.decodableResponseBytes = responseHeader.length();
                client.doDecodeSaslHandshake(traceId);
            }
        }

        return progress;
    }

    protected final int decodeSaslHandshake(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final SaslHandshakeResponseFW saslResponse = saslHandshakeResponseRO.tryWrap(buffer, progress, limit);

            if (saslResponse != null)
            {
                final int errorCode = saslResponse.errorCode();

                progress = saslResponse.limit();

                client.decodableResponseBytes -= saslResponse.sizeof();
                assert client.decodableResponseBytes >= 0;

                client.onDecodeSaslHandshakeResponse(traceId, authorization, errorCode);

                client.decodableMechanisms = saslResponse.mechanismCount();
                client.doDecodeSaslHandshakeMechanisms(traceId);
            }
        }

        return progress;
    }

    protected final int decodeSaslHandshakeMechanisms(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        if (client.decodableMechanisms == 0)
        {
            client.onDecodeSaslResponse(traceId);
            client.doDecodeSaslAuthenticateResponse(traceId);
        }
        else
        {
            client.doDecodeSaslHandshakeMechansim(traceId);
        }

        return progress;
    }

    protected final int decodeSaslHandshakeMechanism(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        decode:
        if (length != 0)
        {
            final SaslHandshakeMechanismResponseFW mechanism = saslHandshakeMechanismResponseRO.tryWrap(buffer, progress, limit);
            if (mechanism == null)
            {
                break decode;
            }

            progress = mechanism.limit();

            client.decodableResponseBytes -= mechanism.sizeof();
            assert client.decodableResponseBytes >= 0;

            client.decodableMechanisms--;
            assert client.decodableMechanisms >= 0;

            client.doDecodeSaslHandshakeMechanisms(traceId);
        }

        return progress;
    }

    protected final int decodeSaslAuthenticateResponse(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final ResponseHeaderFW responseHeader = responseHeaderRO.tryWrap(buffer, progress, limit);
            if (responseHeader != null)
            {
                progress = responseHeader.limit();
                client.decodableResponseBytes = responseHeader.length();
                client.doDecodeSaslAuthenticate(traceId);
            }
        }

        return progress;
    }

    protected final int decodeSaslAuthenticate(
        KafkaSaslClient client,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        final int length = limit - progress;

        if (length != 0)
        {
            final SaslAuthenticateResponseFW authenticateResponse = saslAuthenticateResponseRO.tryWrap(buffer, progress, limit);

            if (authenticateResponse != null)
            {
                final int errorCode = authenticateResponse.errorCode();

                progress = authenticateResponse.limit();

                client.decodableResponseBytes -= authenticateResponse.sizeof();
                assert client.decodableResponseBytes >= 0;

                client.onDecodeSaslResponse(traceId);
                client.onDecodeSaslAuthenticateResponse(traceId, authorization, errorCode);
            }
        }

        return progress;
    }
}
