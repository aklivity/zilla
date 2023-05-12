/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.ws.internal.stream;

import static io.aklivity.zilla.runtime.binding.ws.internal.types.codec.WsHeaderFW.STATUS_NORMAL_CLOSURE;
import static io.aklivity.zilla.runtime.binding.ws.internal.types.codec.WsHeaderFW.STATUS_PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.ws.internal.types.codec.WsHeaderFW.STATUS_UNEXPECTED_CONDITION;
import static io.aklivity.zilla.runtime.binding.ws.internal.util.WsMaskUtil.xor;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.ws.internal.WsBinding;
import io.aklivity.zilla.runtime.binding.ws.internal.WsConfiguration;
import io.aklivity.zilla.runtime.binding.ws.internal.config.WsBindingConfig;
import io.aklivity.zilla.runtime.binding.ws.internal.config.WsRouteConfig;
import io.aklivity.zilla.runtime.binding.ws.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.ws.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.codec.WsHeaderFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.WsBeginExFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.WsDataExFW;
import io.aklivity.zilla.runtime.binding.ws.internal.types.stream.WsEndExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class WsServerFactory implements WsStreamFactory
{
    private static final int MAXIMUM_CONTROL_FRAME_PAYLOAD_SIZE = 125;

    private static final byte[] HANDSHAKE_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(UTF_8);
    private static final String WEBSOCKET_UPGRADE = "websocket";
    private static final String WEBSOCKET_VERSION_13 = "13";
    private static final int MAXIMUM_HEADER_SIZE = 14;

    private static final DirectBuffer CLOSE_PAYLOAD = new UnsafeBuffer(new byte[0]);

    private final MessageDigest sha1 = initSHA1();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final WsBeginExFW.Builder wsBeginExRW = new WsBeginExFW.Builder();
    private final WsDataExFW.Builder wsDataExRW = new WsDataExFW.Builder();
    private final WsEndExFW.Builder wsEndExRW = new WsEndExFW.Builder();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final OctetsFW payloadRO = new OctetsFW();

    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final WsDataExFW wsDataExRO = new WsDataExFW();

    private final WsHeaderFW wsHeaderRO = new WsHeaderFW();
    private final WsHeaderFW.Builder wsHeaderRW = new WsHeaderFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;

    private final Long2ObjectHashMap<WsBindingConfig> bindings;
    private final int wsTypeId;
    private final int httpTypeId;

    public WsServerFactory(
        WsConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.wsTypeId = context.supplyTypeId(WsBinding.NAME);
        this.httpTypeId = context.supplyTypeId("http");
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        WsBindingConfig wsBinding = new WsBindingConfig(binding);
        bindings.put(binding.id, wsBinding);
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
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long traceId = begin.traceId();
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();
        final OctetsFW extension = begin.extension();

        // TODO: need lightweight approach (start)
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::wrap);
        final Map<String, String> headers = new LinkedHashMap<>();
        httpBeginEx.headers().forEach(header ->
        {
            final String name = header.name().asString();
            final String value = header.value().asString();
            headers.merge(name, value, (v1, v2) -> String.format("%s, %s", v1, v2));
        });

        final String scheme = headers.get(":scheme");
        final String authority = headers.get(":authority");
        final String path = headers.get(":path");
        final String upgrade = headers.get("upgrade");
        final String version = headers.get("sec-websocket-version");
        final String key = headers.get("sec-websocket-key");
        final String[] protocols = parseProtocols(headers.get("sec-websocket-protocol"));
        // TODO: need lightweight approach (end)

        MessageConsumer newStream = null;

        if (upgrade == null)
        {
            final long newReplyId = supplyReplyId.applyAsLong(initialId);
            doHttpBegin(sender, originId, routedId, newReplyId, 0L, 0L, 0, traceId, authorization, affinity,
                hs -> hs.item(h -> h.name(":status").value("400"))
                        .item(h -> h.name("connection").value("close")));
            doHttpEnd(sender, originId, routedId, newReplyId, traceId);
            newStream = (t, b, o, l) -> {};
        }
        else if (key != null &&
                WEBSOCKET_UPGRADE.equalsIgnoreCase(upgrade) &&
                WEBSOCKET_VERSION_13.equals(version))
        {
            WsBindingConfig binding = bindings.get(routedId);

            if (binding != null)
            {
                WsRouteConfig route = null;
                String protocol = null;

                for (int i = 0; i < (protocols != null ? protocols.length : 1); i++)
                {
                    String newProtocol = protocols != null ? protocols[i] : null;
                    WsRouteConfig newRoute = binding.resolve(authorization, newProtocol, scheme, authority, path);

                    if (newRoute != null && (route == null || newRoute.order < route.order))
                    {
                        route = newRoute;
                        protocol = newProtocol;
                    }
                }

                if (route != null)
                {
                    newStream = new WsServer(
                        sender,
                        originId,
                        routedId,
                        initialId,
                        route.id,
                        key,
                        protocol,
                        scheme,
                        authority,
                        path)::onNetMessage;
                }
            }
        }

        return newStream;
    }

    private final class WsServer
    {
        private final MessageConsumer receiver;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final String key;
        private final String protocol;
        private final String scheme;
        private final String authority;
        private final String path;

        private WsStream stream;

        private long decodeTraceId;
        private long decodeAuthorization;
        private DecoderState decodeState;

        private MutableDirectBuffer header;
        private int headerLength;

        private MutableDirectBuffer status;
        private int statusLength;

        private int payloadProgress;
        private int payloadLength;
        private int maskingKey;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        public long replyBudgetId;
        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private WsServer(
            MessageConsumer receiver,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            String key,
            String protocol,
            String scheme,
            String authority,
            String path)
        {
            this.receiver = receiver;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.key = key;
            this.protocol = protocol;
            this.scheme = scheme;
            this.authority = authority;
            this.path = path;

            this.header = new UnsafeBuffer(new byte[MAXIMUM_HEADER_SIZE]);
            this.status = new UnsafeBuffer(new byte[2]);

            this.decodeState = this::decodeHeader;
            this.stream = new WsStream(routedId, resolvedId);
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            sha1.reset();
            sha1.update(key.getBytes(US_ASCII));
            final byte[] digest = sha1.digest(HANDSHAKE_GUID);
            final Encoder encoder = Base64.getEncoder();
            final String handshakeHash = new String(encoder.encode(digest), US_ASCII);

            doHttpBegin(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, affinity,
                    setHttpHeaders(handshakeHash, protocol));
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW payload,
            int flags)
        {
            final int payloadSize = payload.sizeof();

            WsHeaderFW wsHeader = wsHeaderRW.wrap(writeBuffer, DataFW.FIELD_OFFSET_PAYLOAD, writeBuffer.capacity())
                                            .length(payloadSize)
                                            .flagsAndOpcode(flags)
                                            .build();

            final int wsHeaderSize = wsHeader.sizeof();

            assert reserved >= wsHeaderSize + payloadSize + replyPad;

            DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                    .payload(p -> p.set((b, o, m) -> wsHeaderSize)
                                   .put(payload.buffer(), payload.offset(), payloadSize))
                    .build();

            receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

            replySeq += reserved;
            assert replySeq <= replyAck + replyMax;
        }

        private void doNetEnd(
            long traceId)
        {
            final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

            receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
        }

        private void doNetAbort(
            long traceId)
        {
            final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .build();

            receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
        }

        private void doNetFlush(
            long traceId,
            long budgetId,
            int reserved)
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(replyId)
                    .sequence(replySeq)
                    .acknowledge(replyAck)
                    .maximum(replyMax)
                    .traceId(traceId)
                    .budgetId(budgetId)
                    .reserved(reserved)
                    .build();

            receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(initialId)
                    .sequence(initialSeq)
                    .acknowledge(initialAck)
                    .maximum(initialMax)
                    .traceId(traceId)
                    .authorization(authorization)
                    .build();

            receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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

            receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int pendingAck,
            int paddingMin)
        {
            long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);
            if (initialAckMax > initialAck || stream.initialMax > initialMax)
            {
                initialAck = initialAckMax;
                initialMax = stream.initialMax;
                assert initialAck <= initialSeq;

                final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .budgetId(budgetId)
                        .padding(paddingMin)
                        .build();

                receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
            case ChallengeFW.TYPE_ID:
                final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onNetChallenge(challenge);
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            assert acknowledge == sequence;
            assert sequence >= initialSeq;
            assert maximum == 0;

            initialSeq = sequence;
            initialAck = acknowledge;

            stream.doAppBegin(traceId, authorization, affinity, protocol, scheme, authority, path);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doNetReset(traceId, authorization);
            }
            else
            {
                decodeTraceId = traceId;
                decodeAuthorization = authorization;

                final OctetsFW payload = data.payload();
                final DirectBuffer buffer = payload.buffer();

                int offset = payload.offset();
                int length = payload.sizeof();
                while (length > 0)
                {
                    int consumed = decodeState.decode(buffer, offset, length);
                    offset += consumed;
                    length -= consumed;
                }

                // Since we have two decoding states for a frame, the following is
                // needed to handle empty close, empty ping etc. Otherwise, it will be
                // delayed until next handleData() (which may not come for e.g empty close frame)
                if (payloadLength == 0)
                {
                    decodeState.decode(buffer, 0, 0);
                }
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            stream.doAppEnd(traceId, authorization, STATUS_NORMAL_CLOSURE);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            stream.doAppAbort(traceId, authorization, STATUS_UNEXPECTED_CONDITION);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            stream.doAppFlush(traceId, authorization, budgetId, reserved);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int maximum = window.maximum();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyBudgetId = budgetId;
            replyAck = acknowledge;
            replyMax = maximum;
            replyPad = padding;

            assert replyAck <= replySeq;

            stream.doAppWindow(traceId, authorization, budgetId, (int)(replySeq - replyAck), replyPad);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            stream.doAppReset(traceId, authorization);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            stream.doAppChallenge(traceId, authorization, extension);
        }

        // @return no bytes consumed to assemble websocket header
        private int assembleHeader(
            DirectBuffer buffer,
            int offset,
            int length)
        {
            int remaining = Math.min(length, MAXIMUM_HEADER_SIZE - headerLength);
            // may copy more than actual header length (up to max header length), but will adjust at the end
            header.putBytes(headerLength, buffer, offset, remaining);

            int consumed = remaining;
            if (headerLength + remaining >= 2)
            {
                int wsHeaderLength = wsHeaderLength(header);
                // eventual headLength must not be more than wsHeaderLength
                if (headerLength + remaining > wsHeaderLength)
                {
                    consumed = wsHeaderLength - headerLength;
                }
            }

            headerLength += consumed;
            return consumed;
        }

        private int decodeHeader(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            int consumed;

            WsHeaderFW wsHeader;
            if (headerLength > 0 || length < MAXIMUM_HEADER_SIZE)
            {
                consumed = assembleHeader(buffer, offset, length);
                if (headerLength >= 2 && headerLength == wsHeaderLength(header))
                {
                    wsHeader = wsHeaderRO.wrap(header, 0, headerLength);
                    headerLength = 0;
                }
                else
                {
                    return consumed;            // partial header
                }
            }
            else
            {
                // No need to assemble header as complete header is available
                wsHeader = wsHeaderRO.wrap(buffer, offset, offset + MAXIMUM_HEADER_SIZE);
                consumed = wsHeader.sizeof();
            }

            if (wsHeader.mask() && wsHeader.maskingKey() != 0L)
            {
                this.maskingKey = wsHeader.maskingKey();
                this.payloadLength = wsHeader.length();
                this.payloadProgress = 0;

                switch (wsHeader.opcode())
                {
                case 0x00:
                    this.decodeState = this::decodeContinuation;
                    break;
                case 0x01:
                    this.decodeState = this::decodeText;
                    break;
                case 0x02:
                    this.decodeState = this::decodeBinary;
                    break;
                case 0x08:
                    this.decodeState = this::decodeClose;
                    break;
                case 0x0a:
                    this.decodeState = this::decodePong;
                    break;
                default:
                    this.decodeState = this::decodeUnexpected;
                    break;
                }
            }
            else
            {
                doNetReset(decodeTraceId, decodeAuthorization);
                stream.doAppAbort(decodeTraceId, decodeAuthorization, STATUS_PROTOCOL_ERROR);
            }

            return consumed;
        }

        private int decodeContinuation(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            // TODO: limit acceptReply bytes by acceptReply window, or RESET on overflow?

            final int decodeBytes = Math.min(length, payloadLength - payloadProgress);

            final OctetsFW payload = payloadRO.wrap(buffer, offset, offset + decodeBytes);
            stream.doAppData(decodeTraceId, decodeAuthorization, 0x80, maskingKey, payload);

            payloadProgress += decodeBytes;
            maskingKey = rotateMaskingKey(maskingKey, decodeBytes);

            if (payloadProgress == payloadLength)
            {
                this.decodeState = this::decodeHeader;
            }

            return decodeBytes;
        }

        private int decodeText(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            // TODO canWrap for UTF-8 split multi-byte characters

            // TODO: limit acceptReply bytes by acceptReply window, or RESET on overflow?

            final int decodeBytes = Math.min(length, payloadLength - payloadProgress);

            final OctetsFW payload = payloadRO.wrap(buffer, offset, offset + decodeBytes);
            stream.doAppData(decodeTraceId, decodeAuthorization, 0x81, maskingKey, payload);

            payloadProgress += decodeBytes;
            maskingKey = rotateMaskingKey(maskingKey, decodeBytes);

            if (payloadProgress == payloadLength)
            {
                this.decodeState = this::decodeHeader;
            }

            return decodeBytes;
        }

        private int decodeBinary(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            // TODO: limit acceptReply bytes by acceptReply window, or RESET on overflow?

            final int decodeBytes = Math.min(length, payloadLength - payloadProgress);

            final OctetsFW payload = payloadRO.wrap(buffer, offset, offset + decodeBytes);
            stream.doAppData(decodeTraceId, decodeAuthorization, 0x82, maskingKey, payload);

            payloadProgress += decodeBytes;
            maskingKey = rotateMaskingKey(maskingKey, decodeBytes);

            if (payloadProgress == payloadLength)
            {
                this.decodeState = this::decodeHeader;
            }

            return decodeBytes;
        }

        private int decodeClose(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            if (payloadLength > MAXIMUM_CONTROL_FRAME_PAYLOAD_SIZE)
            {
                doNetReset(decodeTraceId, decodeAuthorization);
                stream.doAppAbort(decodeTraceId, decodeAuthorization, STATUS_PROTOCOL_ERROR);
                return length;
            }
            else
            {
                final int decodeBytes = Math.min(length, payloadLength - payloadProgress);
                payloadProgress += decodeBytes;

                int remaining = Math.min(length, 2 - statusLength);
                if (remaining > 0)
                {
                    status.putBytes(statusLength, buffer, offset, remaining);
                    statusLength += remaining;
                }

                if (payloadProgress == payloadLength)
                {
                    short code = STATUS_NORMAL_CLOSURE;
                    if (statusLength == 2)
                    {
                        xor(status, 0, 2, maskingKey);
                        code = status.getShort(0, ByteOrder.BIG_ENDIAN);
                    }
                    statusLength = 0;
                    stream.doAppEnd(decodeTraceId, decodeAuthorization, code);
                    this.decodeState = this::decodeHeader;
                }

                return decodeBytes;
            }
        }

        private int decodePong(
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            if (payloadLength > MAXIMUM_CONTROL_FRAME_PAYLOAD_SIZE)
            {
                doNetReset(decodeTraceId, decodeAuthorization);
                stream.doAppAbort(decodeTraceId, decodeAuthorization, STATUS_PROTOCOL_ERROR);
                return length;
            }
            else
            {
                final int decodeBytes = Math.min(length, payloadLength - payloadProgress);

                payloadRO.wrap(buffer, offset, offset + decodeBytes);

                payloadProgress += decodeBytes;
                maskingKey = rotateMaskingKey(maskingKey, decodeBytes);

                if (payloadProgress == payloadLength)
                {
                    this.decodeState = this::decodeHeader;
                }

                return decodeBytes;
            }
        }

        private int decodeUnexpected(
            final DirectBuffer directBuffer,
            final int offset,
            final int length)
        {
            doNetReset(decodeTraceId, decodeAuthorization);
            stream.doAppAbort(decodeTraceId, decodeAuthorization, STATUS_PROTOCOL_ERROR);
            return length;
        }

        private int rotateMaskingKey(
            int maskingKey,
            int decodeBytes)
        {
            decodeBytes = decodeBytes % 4;
            int left;
            int right;
            if (nativeOrder() == BIG_ENDIAN)
            {
                left = decodeBytes * 8;
                right = Integer.SIZE - left;
            }
            else
            {
                right = decodeBytes * 8;
                left = Integer.SIZE - right;
            }
            return (maskingKey << left) | (maskingKey >>> right);
        }

        private final class WsStream
        {
            private MessageConsumer application;
            private final long originId;
            private final long routedId;
            private final long initialId;
            private final long replyId;

            private long initialBudgetId;
            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;

            private long replySeq;
            private long replyAck;
            private int replyMax;

            private WsStream(
                long originId,
                long routedId)
            {
                this.originId = originId;
                this.routedId = routedId;
                this.initialId = supplyInitialId.applyAsLong(routedId);
                this.replyId = supplyReplyId.applyAsLong(initialId);
            }

            private void doAppBegin(
                long traceId,
                long authorization,
                long affinity,
                String protocol,
                String scheme,
                String authority,
                String path)
            {
                final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .affinity(affinity)
                        .extension(e -> e.set(visitWsBeginEx(protocol, scheme, authority, path)))
                        .build();

                application =
                    streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), this::onAppMessage);

                application.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
            }

            private void doAppData(
                long traceId,
                long authorization,
                int flags,
                int maskingKey,
                OctetsFW payload)
            {
                final int reserved = payload.sizeof() + initialPad;

                final int capacity = payload.sizeof();
                final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .budgetId(initialBudgetId)
                        .reserved(reserved)
                        .payload(p -> p.set(payload).set((b, o, l) -> xor(b, o, o + capacity, maskingKey)))
                        .extension(e -> e.set(visitWsDataEx(flags)))
                        .build();

                application.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());

                initialSeq += reserved;
                assert initialSeq <= initialAck + initialMax;
            }

            private void doAppEnd(
                long traceId,
                long authorization,
                short code)
            {
                final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .extension(e -> e.set(visitWsEndEx(code)))
                        .build();

                application.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
            }

            private void doAppAbort(
                long traceId,
                long authorization,
                short code)
            {
                // TODO: WsAbortEx
                final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .build();

                application.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
            }

            private void doAppFlush(
                long traceId,
                long authorization,
                long budgetId,
                int reserved)
            {
                final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(initialId)
                        .sequence(initialSeq)
                        .acknowledge(initialAck)
                        .maximum(initialMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .budgetId(budgetId)
                        .reserved(reserved)
                        .build();

                application.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }

            private void doAppReset(
                long traceId,
                long authorization)
            {
                final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(originId)
                        .routedId(routedId)
                        .streamId(replyId)
                        .sequence(replySeq)
                        .acknowledge(replyAck)
                        .maximum(replyMax)
                        .traceId(traceId)
                        .authorization(authorization)
                        .build();

                application.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
            }

            private void doAppChallenge(
                long traceId,
                long authorization,
                OctetsFW extension)
            {
                final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

                application.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
            }

            private void doAppWindow(
                long traceId,
                long authorization,
                long budgetId,
                int pendingAck,
                int paddingMin)
            {
                long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
                if (replyAckMax > replyAck || WsServer.this.replyMax > replyMax)
                {
                    replyAck = replyAckMax;
                    replyMax = WsServer.this.replyMax;
                    assert replyAck <= replySeq;

                    int replyPad = paddingMin + MAXIMUM_HEADER_SIZE;

                    final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                            .originId(originId)
                            .routedId(routedId)
                            .streamId(replyId)
                            .sequence(replySeq)
                            .acknowledge(replyAck)
                            .maximum(replyMax)
                            .traceId(traceId)
                            .authorization(authorization)
                            .budgetId(budgetId)
                            .padding(replyPad)
                            .build();

                    application.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
                case ChallengeFW.TYPE_ID:
                    final ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                    onAppChallenge(challenge);
                    break;
                }
            }

            private void onAppBegin(
                BeginFW begin)
            {
                final long traceId = begin.traceId();
                final long authorization = begin.authorization();
                final long affinity = begin.affinity();

                doNetBegin(traceId, authorization, affinity);
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

                replySeq = sequence + reserved;

                assert replyAck <= replySeq;

                if (replySeq > replyAck + replyMax)
                {
                    doAppReset(traceId, authorization);
                }
                else
                {
                    final OctetsFW payload = data.payload();
                    final OctetsFW extension = data.extension();

                    int flags = 0x82;
                    if (extension.sizeof() > 0)
                    {
                        final WsDataExFW wsDataEx = extension.get(wsDataExRO::wrap);
                        flags = wsDataEx.flags();
                    }

                    doNetData(traceId, authorization, budgetId, reserved, payload, flags);
                }
            }

            private void onAppEnd(
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

                final OctetsFW payload = payloadRO.wrap(CLOSE_PAYLOAD, 0, 0);
                final int reserved = payload.sizeof() + MAXIMUM_HEADER_SIZE + WsServer.this.replyPad;

                doNetData(traceId, authorization, WsServer.this.replyBudgetId, reserved, payload, 0x88);
                doNetEnd(traceId);
            }

            private void onAppAbort(
                AbortFW abort)
            {
                final long sequence = abort.sequence();
                final long acknowledge = abort.acknowledge();
                final long traceId = abort.traceId();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;

                replySeq = sequence;

                assert replyAck <= replySeq;

                doNetAbort(traceId);
            }

            private void onAppFlush(
                FlushFW flush)
            {
                final long sequence = flush.sequence();
                final long acknowledge = flush.acknowledge();
                final long traceId = flush.traceId();
                final long budgetId = flush.budgetId();
                final int reserved = flush.reserved();

                assert acknowledge <= sequence;
                assert sequence >= replySeq;

                replySeq = sequence;

                assert replyAck <= replySeq;

                doNetFlush(traceId, budgetId, reserved);
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
                assert acknowledge >= initialAck;
                assert maximum + acknowledge >= initialMax + initialAck;

                initialBudgetId = budgetId;
                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;

                assert initialAck <= initialSeq;

                doNetWindow(traceId, authorization, budgetId, (int)(initialSeq - initialAck), initialPad);
            }

            private void onAppReset(
                ResetFW reset)
            {
                final long sequence = reset.sequence();
                final long acknowledge = reset.acknowledge();
                final long authorization = reset.authorization();
                final long traceId = reset.traceId();

                assert acknowledge <= sequence;
                assert acknowledge >= initialAck;

                initialAck = acknowledge;

                assert initialAck <= initialSeq;

                doNetReset(traceId, authorization);
            }

            private void onAppChallenge(
                ChallengeFW challenge)
            {
                final long sequence = challenge.sequence();
                final long acknowledge = challenge.acknowledge();
                final long authorization = challenge.authorization();
                final long traceId = challenge.traceId();
                final OctetsFW extension = challenge.extension();

                assert acknowledge <= sequence;
                assert acknowledge >= initialAck;

                initialAck = acknowledge;

                assert initialAck <= initialSeq;

                doNetChallenge(traceId, authorization, extension);
            }
        }
    }

    private void doHttpBegin(
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
        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

    private void doHttpEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .traceId(traceId)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private Flyweight.Builder.Visitor visitWsDataEx(
        int flags)
    {
        return (buffer, offset, limit) ->
            wsDataExRW.wrap(buffer, offset, limit)
                      .typeId(wsTypeId)
                      .flags(flags)
                      .build()
                      .sizeof();
    }

    private Flyweight.Builder.Visitor visitWsBeginEx(
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        return (buffer, offset, limit) ->
            (protocol == null && scheme == null && authority == null && path == null) ? 0 :
            wsBeginExRW.wrap(buffer, offset, limit)
                       .typeId(wsTypeId)
                       .protocol(protocol)
                       .scheme(scheme)
                       .authority(authority)
                       .path(path)
                       .build()
                       .sizeof();
    }

    private Flyweight.Builder.Visitor visitWsEndEx(
        short code)
    {
        return (buffer, offset, limit) ->
            wsEndExRW.wrap(buffer, offset, limit)
                     .typeId(wsTypeId)
                     .code(code)
                     .reason("")
                     .build()
                     .sizeof();
    }

    private Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> setHttpHeaders(
        String handshakeHash,
        String protocol)
    {
        return headers ->
        {
            headers.item(h -> h.name(":status").value("101"));
            headers.item(h -> h.name("upgrade").value("websocket"));
            headers.item(h -> h.name("connection").value("upgrade"));
            headers.item(h -> h.name("sec-websocket-accept").value(handshakeHash));

            if (protocol != null)
            {
                headers.item(h -> h.name("sec-websocket-protocol").value(protocol));
            }
        };
    }

    private static String[] parseProtocols(
        final String protocols)
    {
        return (protocols != null) ? protocols.split("\\s*,\\s*") : null;
    }

    private static int wsHeaderLength(
        DirectBuffer buffer)
    {
        int wsHeaderLength = 2;
        byte secondByte = buffer.getByte(1);
        wsHeaderLength += lengthSize(secondByte) - 1;

        if (isMasked(secondByte))
        {
            wsHeaderLength += 4;
        }

        return wsHeaderLength;
    }

    private static boolean isMasked(
        byte b)
    {
        return (b & 0x80) != 0;
    }

    private static int lengthSize(
        byte b)
    {
        switch (b & 0x7f)
        {
        case 0x7e:
            return 3;
        case 0x7f:
            return 9;
        default:
            return 1;
        }
    }

    private static MessageDigest initSHA1()
    {
        try
        {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
            return null;
        }
    }

    @FunctionalInterface
    private interface DecoderState
    {
        int decode(DirectBuffer buffer, int offset, int length);
    }
}
