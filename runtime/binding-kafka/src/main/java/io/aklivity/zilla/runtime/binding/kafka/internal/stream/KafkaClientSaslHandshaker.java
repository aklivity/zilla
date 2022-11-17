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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.ScramMechanism;
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
    private static final int ERROR_SASL_AUTHENTICATION_FAILED = 58;
    private static final int ERROR_NONE = 0;

    private static final String CLIENT_KEY = "Client Key";
    private static final String SERVER_KEY = "Server Key";
    private static final String PRINTABLE = "[\\x21-\\x7E&&[^,]]+";
    private static final String BASE64_CHAR = "[a-zA-Z0-9/+]";
    private static final String BASE64 = String.format("(?:%s{4})*(?:%s{3}=|%s{2}==)?", BASE64_CHAR, BASE64_CHAR, BASE64_CHAR);
    private static final Pattern SASL_SCRAM_SERVER_FIRST_MESSAGE = Pattern.compile(String.format(
            "r=(?<nonce>%s),s=(?<salt>%s),i=(?<iterations>[0-9]+)", PRINTABLE, BASE64));
    private static final Pattern SASL_SCRAM_SERVER_FINAL_MESSAGE = Pattern.compile(String.format(
            "(?:v=(?<verifier>%s))", BASE64));
    private static final String AUTH_MESSAGE_FORMAT = "n=%s,r=%s,r=%s,s=%s,i=%s,c=%s,r=%s";
    private static final byte[] SASL_SCRAM_CHANNEL_BINDING = "n,,".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SASL_SCRAM_USERNAME = "n=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SASL_SCRAM_RANDOM = ",r=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SASL_SCRAM_CHANNEL = "c=".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SASL_SCRAM_SALT_PASSWORD = ",p=".getBytes(StandardCharsets.US_ASCII);
    private static final String SASL_SCRAM_CHANNEL_RANDOM = Base64.getEncoder().encodeToString(SASL_SCRAM_CHANNEL_BINDING);

    private final RequestHeaderFW.Builder requestHeaderRW = new RequestHeaderFW.Builder();
    private final SaslHandshakeRequestFW.Builder saslHandshakeRequestRW = new SaslHandshakeRequestFW.Builder();
    private final SaslAuthenticateRequestFW.Builder saslAuthenticateRequestRW = new SaslAuthenticateRequestFW.Builder();

    private final ResponseHeaderFW responseHeaderRO = new ResponseHeaderFW();
    private final SaslHandshakeResponseFW saslHandshakeResponseRO = new SaslHandshakeResponseFW();
    private final SaslHandshakeMechanismResponseFW saslHandshakeMechanismResponseRO = new SaslHandshakeMechanismResponseFW();
    private final SaslAuthenticateResponseFW saslAuthenticateResponseRO = new SaslAuthenticateResponseFW();

    private KafkaSaslClientDecoder decodeSaslPlainAuthenticate = this::decodeSaslPlainAuthenticate;
    private KafkaSaslClientDecoder decodeSaslScramAuthenticateFirst = this::decodeSaslScramAuthenticateFirst;
    private KafkaSaslClientDecoder decodeSaslScramAuthenticateFinal = this::decodeSaslScramAuthenticateFinal;

    private final MutableDirectBuffer scramBuffer = new UnsafeBuffer(new byte[1024]);
    private MessageDigest messageDigest;
    private Mac mac;
    private Supplier<String> nonceSupplier;
    private ScramMechanism mechanism;
    private Matcher serverResponseMatcher;
    private byte[] result, ui, prev;

    protected final LongUnaryOperator supplyInitialId;
    protected final LongUnaryOperator supplyReplyId;
    protected final MutableDirectBuffer writeBuffer;

    public KafkaClientSaslHandshaker(
        KafkaConfiguration config,
        EngineContext context)
    {
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.nonceSupplier = config.nonceSupplier();
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
        private LongLongConsumer encodeSaslAuthenticate;
        private KafkaSaslClientDecoder decodeSaslAuthenticate;


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
            encodeSaslAuthenticate.accept(traceId, budgetId);
        }

        private void doEncodeSaslPlainAuthenticateRequest(
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

        private void doEncodeSaslScramFirstAuthenticateRequest(
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
            clientNonce = nonceSupplier.get();

            int scramBytes = 0;
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_CHANNEL_BINDING);
            scramBytes += SASL_SCRAM_CHANNEL_BINDING.length;
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_USERNAME);
            scramBytes += SASL_SCRAM_USERNAME.length;
            scramBytes += scramBuffer.putStringWithoutLengthUtf8(scramBytes, username);
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_RANDOM);
            scramBytes += SASL_SCRAM_RANDOM.length;
            scramBuffer.putBytes(scramBytes, clientNonce.getBytes());
            scramBytes += clientNonce.getBytes().length;

            final SaslAuthenticateRequestFW authenticateRequest =
                    saslAuthenticateRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                            .authBytes(scramBuffer, 0, scramBytes)
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

        private void doEncodeSaslScramFinalAuthenticateRequest(
                long traceId,
                long budgetId)
        {
            mechanism = ScramMechanism.forMechanismName(sasl.mechanism.toUpperCase());
            try
            {
                messageDigest = MessageDigest.getInstance(mechanism.hashAlgorithm());
                mac = Mac.getInstance(mechanism.macAlgorithm());
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
            saltedPassword = hi(sasl.password.getBytes(StandardCharsets.US_ASCII),
                    Base64.getDecoder().decode(toBytes(salt)),
                    Integer.parseInt(iterationCount));
            authMessage = toBytes(String.format(AUTH_MESSAGE_FORMAT,
                    sasl.username, clientNonce, serverNonce, salt, iterationCount, SASL_SCRAM_CHANNEL_RANDOM, serverNonce));

            int scramBytes = 0;
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_CHANNEL);
            scramBytes += SASL_SCRAM_CHANNEL.length;
            scramBytes += scramBuffer.putStringWithoutLengthUtf8(scramBytes, SASL_SCRAM_CHANNEL_RANDOM);
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_RANDOM);
            scramBytes += SASL_SCRAM_RANDOM.length;
            scramBytes += scramBuffer.putStringWithoutLengthUtf8(scramBytes, serverNonce);
            scramBuffer.putBytes(scramBytes, SASL_SCRAM_SALT_PASSWORD);
            scramBytes += SASL_SCRAM_SALT_PASSWORD.length;
            scramBytes += scramBuffer.putStringWithoutLengthUtf8(scramBytes, clientProof(saltedPassword, authMessage));

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

            final SaslAuthenticateRequestFW authenticateRequest =
                    saslAuthenticateRequestRW.wrap(encodeBuffer, encodeProgress, encodeLimit)
                            .authBytes(scramBuffer, 0, scramBytes)
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
                if (errorCode == ERROR_NONE)
                {
                    switch (client.sasl.mechanism)
                    {
                    case "plain" :
                        client.encodeSaslAuthenticate = client::doEncodeSaslPlainAuthenticateRequest;
                        client.decodeSaslAuthenticate = decodeSaslPlainAuthenticate;
                        break;
                    case "scram-sha-1" :
                    case "scram-sha-256" :
                    case "scram-sha-512" :
                        client.encodeSaslAuthenticate = client::doEncodeSaslScramFirstAuthenticateRequest;
                        client.decodeSaslAuthenticate = decodeSaslScramAuthenticateFirst;
                        break;
                    }
                }
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
        MutableDirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return client.decodeSaslAuthenticate.decode(
                client, traceId, authorization,
                budgetId, reserved, buffer,
                offset, progress, limit);
    }

    private int decodeSaslPlainAuthenticate(
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

    private int decodeSaslScramAuthenticateFirst(
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

                DirectBuffer serverFirstResponse = authenticateResponse.authBytes().value();
                String serverFirstMessage = serverFirstResponse.getStringWithoutLengthUtf8(0,
                        serverFirstResponse.capacity());

                serverResponseMatcher = SASL_SCRAM_SERVER_FIRST_MESSAGE.matcher(serverFirstMessage);
                if (serverResponseMatcher.matches())
                {
                    client.serverNonce = serverResponseMatcher.group("nonce");
                    client.salt = serverResponseMatcher.group("salt");
                    client.iterationCount = serverResponseMatcher.group("iterations");
                }
                if (errorCode == ERROR_NONE && client.serverNonce != null &&
                        client.serverNonce.startsWith(client.clientNonce))
                {
                    client.encodeSaslAuthenticate = client::doEncodeSaslScramFinalAuthenticateRequest;
                    client.decodeSaslAuthenticate = decodeSaslScramAuthenticateFinal;
                    client.onDecodeSaslResponse(traceId);
                }
                else
                {
                    client.onDecodeSaslResponse(traceId);
                    client.onDecodeSaslAuthenticateResponse(traceId, authorization, ERROR_SASL_AUTHENTICATION_FAILED);
                }
            }
        }

        return progress;
    }

    private int decodeSaslScramAuthenticateFinal(
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

                byte[] serverKey = serverKey(client.saltedPassword);
                byte[] serverSignature = hmac(serverKey, client.authMessage);
                DirectBuffer serverFinalResponse = authenticateResponse.authBytes().value();
                String serverFinalMessage = serverFinalResponse.getStringWithoutLengthUtf8(0,
                        serverFinalResponse.capacity());
                serverResponseMatcher.reset();
                serverResponseMatcher = SASL_SCRAM_SERVER_FINAL_MESSAGE.matcher(serverFinalMessage);
                if (serverResponseMatcher.matches())
                {
                    serverFinalMessage = serverResponseMatcher.group("verifier");
                }
                if (!Arrays.equals(Base64.getDecoder().decode(serverFinalMessage),
                        serverSignature))
                {
                    client.onDecodeSaslResponse(traceId);
                    client.onDecodeSaslAuthenticateResponse(traceId, authorization, ERROR_SASL_AUTHENTICATION_FAILED);
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

    public byte[] hmac(byte[] key, byte[] bytes)
    {
        try
        {
            mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        return mac.doFinal(bytes);
    }

    public byte[] xor(byte[] first, byte[] second)
    {
        result = new byte[first.length];
        if (first.length == second.length)
        {
            for (int i = 0; i < result.length; i++)
            {
                result[i] = (byte) (first[i] ^ second[i]);
            }
        }
        return result;
    }

    public byte[] hi(byte[] str, byte[] salt, int iterations)
    {
        try
        {
            mac.init(new SecretKeySpec(str, mac.getAlgorithm()));
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        mac.update(salt);
        result = prev = mac.doFinal(new byte[]{0, 0, 0, 1});
        for (int i = 2; i <= iterations; i++)
        {
            ui = hmac(str, prev);
            result = xor(result, ui);
            prev = ui;
        }
        return result;
    }

    public byte[] hash(byte[] str)
    {
        return messageDigest.digest(str);
    }

    public byte[] clientKey(byte[] saltedPassword)
    {
        return hmac(saltedPassword, toBytes(CLIENT_KEY));
    }

    public byte[] serverKey(byte[] saltedPassword)
    {
        return hmac(saltedPassword, toBytes(SERVER_KEY));
    }

    public String clientProof(byte[] saltedPassword, byte[] authMessage)
    {
        byte[] clientKey = clientKey(saltedPassword);
        return Base64.getEncoder().encodeToString(xor(clientKey,
                hmac(hash(clientKey), authMessage)));
    }

    public byte[] toBytes(String str)
    {
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
