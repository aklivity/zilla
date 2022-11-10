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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.ScramFormatter;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.ScramMechanism;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String8FW;
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

    private static final Pattern SERVER_FIRST_MESSAGE = Pattern.compile("r=([^,]*),s=([^,]*),i=(.*)$");
    private static final Pattern SERVER_FINAL_MESSAGE = Pattern.compile("v=([^,]*)$");

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final SaslHandshakeRequestFW.Builder saslHandshakeRequestRW = new SaslHandshakeRequestFW.Builder();
    private final SaslAuthenticateRequestFW.Builder saslAuthenticateRequestRW = new SaslAuthenticateRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final SaslHandshakeResponseFW saslHandshakeResponseRO = new SaslHandshakeResponseFW();
    private final SaslHandshakeMechanismResponseFW saslHandshakeMechanismResponseRO = new SaslHandshakeMechanismResponseFW();
    private final SaslAuthenticateResponseFW saslAuthenticateResponseRO = new SaslAuthenticateResponseFW();
    private ScramFormatter formatter;

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
        private String clientNonce;
        private String serverNonce;
        private String salt;
        private String iterationCount;
        byte[] saltedPassword;
        byte[] authMessage;


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

            if (sasl.mechanism != null && ScramMechanism.isScram(sasl.mechanism.toUpperCase()))
            {
                //clientNonce = new BigInteger(130, new SecureRandom()).toString(Character.MAX_RADIX);
                clientNonce = "fyko+d2lbbFgONRv9qkxdawL";
                DirectBuffer clientFirstMessage = new String8FW(String.format("n,,n=%s,r=%s", username, clientNonce))
                        .value();
                final SaslAuthenticateRequestFW authenticateRequest =
                        saslAuthenticateRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                                .authBytes(clientFirstMessage, 0, clientFirstMessage.capacity())
                                .build();
                encodeProgress = authenticateRequest.limit();
            }
            else
            {
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
            }

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

        protected final void doEncodeSaslScramAuthenticateRequest(
                long traceId,
                long budgetId)
        {
            try
            {
                formatter = new ScramFormatter(ScramMechanism.forMechanismName(sasl.mechanism.toUpperCase()));

                String channelBinding = Base64.getEncoder().encodeToString("n,,".getBytes(Charset.forName("ASCII")));
                String clientFirstMessageBare = String.format("n=%s,r=%s", sasl.username, clientNonce);
                String serverFirstMessage = String.format("r=%s,s=%s,i=%s", serverNonce, salt, iterationCount);
                String clientFinalMessageWithoutProof = String.format("c=%s,r=%s", channelBinding, serverNonce);

                saltedPassword = formatter.hi(sasl.password.getBytes(StandardCharsets.US_ASCII),
                        Base64.getDecoder().decode(formatter.toBytes(salt)),
                        Integer.parseInt(iterationCount));

                authMessage = formatter.toBytes(clientFirstMessageBare + "," +
                        serverFirstMessage + "," + clientFinalMessageWithoutProof);
                byte[] clientProof = formatter.clientProof(saltedPassword, authMessage);

                String clientProofStr = Base64.getEncoder().encodeToString(clientProof);
                String clientFinalMessage = String.format("%s,p=%s", clientFinalMessageWithoutProof, clientProofStr);

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

                DirectBuffer clientFinalMessageBuffer = new String8FW(clientFinalMessage).value();
                final SaslAuthenticateRequestFW authenticateRequest =
                        saslAuthenticateRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                                .authBytes(clientFinalMessageBuffer, 0, clientFinalMessageBuffer.capacity())
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

                doNetworkData(traceId, budgetId, encodeBuffer, encodeOffset, encodeProgress);

                doDecodeSaslScramAuthenticateResponse(traceId);

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
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

        protected abstract void doDecodeSaslScramAuthenticateResponse(
                long traceId);

        protected abstract void doDecodeSaslAuthenticate(
            long traceId);

        protected abstract void doDecodeSaslScramAuthenticate(
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

    protected final int decodeSaslScramAuthenticateResponse(
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
                client.doDecodeSaslScramAuthenticate(traceId);
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

                if (client.sasl != null && client.sasl.mechanism != null &&
                        ScramMechanism.isScram(client.sasl.mechanism.toUpperCase()))
                {
                    DirectBuffer serverFirstResponse = authenticateResponse.authBytes().value();
                    String serverFirstMessage = serverFirstResponse.getStringWithoutLengthUtf8(0,
                            serverFirstResponse.capacity());

                    Matcher serverScramResponse = SERVER_FIRST_MESSAGE.matcher(serverFirstMessage);
                    while (serverScramResponse.find())
                    {
                        client.serverNonce = serverScramResponse.group(1);
                        client.salt = serverScramResponse.group(2);
                        client.iterationCount = serverScramResponse.group(3);
                    }
                    if (errorCode ==  0 && client.serverNonce.startsWith(client.clientNonce))
                    {
                        client.onDecodeSaslResponse(traceId);
                        client.doEncodeSaslScramAuthenticateRequest(traceId, budgetId);
                    }
                    else
                    {
                        client.onDecodeSaslResponse(traceId);
                        client.onDecodeSaslAuthenticateResponse(traceId, authorization, 58);
                    }
                }
                else
                {
                    client.onDecodeSaslResponse(traceId);
                    client.onDecodeSaslAuthenticateResponse(traceId, authorization, errorCode);
                }
            }
        }

        return progress;
    }

    protected final int decodeSaslScramAuthenticate(
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

                try
                {
                    byte[] serverKey = formatter.serverKey(client.saltedPassword);
                    byte[] serverSignature = formatter.hmac(serverKey, client.authMessage);
                    DirectBuffer serverFinalResponse = authenticateResponse.authBytes().value();
                    String serverFinalMessage = serverFinalResponse.getStringWithoutLengthUtf8(0,
                            serverFinalResponse.capacity());

                    Matcher serverFinalResponseMatcher = SERVER_FINAL_MESSAGE.matcher(serverFinalMessage);
                    while (serverFinalResponseMatcher.find())
                    {
                        serverFinalMessage = serverFinalResponseMatcher.group(1);
                    }
                    if (!Arrays.equals(Base64.getDecoder().decode(serverFinalMessage),
                            serverSignature))
                    {
                        client.onDecodeSaslResponse(traceId);
                        client.onDecodeSaslAuthenticateResponse(traceId, authorization, 58);
                    }
                    else
                    {
                        client.onDecodeSaslResponse(traceId);
                        client.onDecodeSaslAuthenticateResponse(traceId, authorization, errorCode);
                    }

                }
                catch (InvalidKeyException e)
                {
                    e.printStackTrace();
                }
            }
        }

        return progress;
    }
}
