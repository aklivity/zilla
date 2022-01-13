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
package io.aklivity.zilla.runtime.cog.proxy.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.nio.ByteBuffer;
import java.util.function.LongUnaryOperator;
import java.util.zip.CRC32C;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.proxy.internal.ProxyBinding;
import io.aklivity.zilla.runtime.cog.proxy.internal.ProxyConfiguration;
import io.aklivity.zilla.runtime.cog.proxy.internal.config.ProxyBindingConfig;
import io.aklivity.zilla.runtime.cog.proxy.internal.config.ProxyRouteConfig;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.Flyweight;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressProtocol;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.String16FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyAddrFamily;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyAddrInet4FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyAddrInet6FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyAddrProtocol;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyAddrUnixFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyTlvFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyTlvSslFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class ProxyServerFactory implements ProxyStreamFactory
{
    private static final DirectBuffer HEADER_V2 = new UnsafeBuffer("\r\n\r\n\0\r\nQUIT\n".getBytes(US_ASCII));
    private static final int HEADER_V2_SIZE = HEADER_V2.capacity();
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(0, 0);
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);

    private static final int PROXY_ADDRESS_LENGTH_INET4 = 12;
    private static final int PROXY_ADDRESS_LENGTH_INET6 = 36;
    private static final int PROXY_ADDRESS_LENGTH_UNIX = 216;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();

    private final ProxyBeginExFW beginExRO = new ProxyBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final OctetsFW payloadRO = new OctetsFW();

    private final ProxyAddrInet4FW addressInet4RO = new ProxyAddrInet4FW();
    private final ProxyAddrInet6FW addressInet6RO = new ProxyAddrInet6FW();
    private final ProxyAddrUnixFW addressUnixRO = new ProxyAddrUnixFW();
    private final ProxyTlvFW tlvRO = new ProxyTlvFW();
    private final OctetsFW tlvBoundedRO = new OctetsFW();
    private final String16FW tlvStringRO = new String16FW(BIG_ENDIAN);
    private final ProxyTlvSslFW tlvSslRO = new ProxyTlvSslFW();

    private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();
    private final ProxyInfoFW.Builder infoRW = new ProxyInfoFW.Builder();

    private final ProxyNetServerDecoder decodeHeader = this::decodeHeader;
    private final ProxyNetServerDecoder decodeVersion = this::decodeVersion;
    private final ProxyNetServerDecoder decodeCommand = this::decodeCommand;
    private final ProxyNetServerDecoder decodeLocal = this::decodeLocal;
    private final ProxyNetServerDecoder decodeProxy = this::decodeProxy;
    private final ProxyNetServerDecoder decodeProxyInet4 = this::decodeProxyInet4;
    private final ProxyNetServerDecoder decodeProxyInet6 = this::decodeProxyInet6;
    private final ProxyNetServerDecoder decodeProxyUnix = this::decodeProxyUnix;
    private final ProxyNetServerDecoder decodeProxyTlv = this::decodeProxyTlv;
    private final ProxyNetServerDecoder decodeProxyTlvAlpn = this::decodeProxyTlvAlpn;
    private final ProxyNetServerDecoder decodeProxyTlvAuthority = this::decodeProxyTlvAuthority;
    private final ProxyNetServerDecoder decodeProxyTlvCrc32c = this::decodeProxyTlvCrc32c;
    private final ProxyNetServerDecoder decodeProxyTlvIgnore = this::decodeProxyTlvIgnore;
    private final ProxyNetServerDecoder decodeProxyTlvUniqueId = this::decodeProxyTlvUniqueId;
    private final ProxyNetServerDecoder decodeProxyTlvSsl = this::decodeProxyTlvSsl;
    private final ProxyNetServerDecoder decodeProxyTlvNetns = this::decodeProxyTlvNetns;
    private final ProxyNetServerDecoder decodeProxyTlvSslSubTlv = this::decodeProxyTlvSslSubTlv;
    private final ProxyNetServerDecoder decodeProxyTlvSslSubTlvIgnore = this::decodeProxyTlvSslSubTlvIgnore;
    private final ProxyNetServerDecoder decodeProxyTlvSslVersion = this::decodeProxyTlvSslVersion;
    private final ProxyNetServerDecoder decodeProxyTlvSslCommonName = this::decodeProxyTlvSslCommonName;
    private final ProxyNetServerDecoder decodeProxyTlvSslCipher = this::decodeProxyTlvSslCipher;
    private final ProxyNetServerDecoder decodeProxyTlvSslSignature = this::decodeProxyTlvSslSignature;
    private final ProxyNetServerDecoder decodeProxyTlvSslKey = this::decodeProxyTlvSslKey;
    private final ProxyNetServerDecoder decodeIgnore = this::decodeIgnore;
    private final ProxyNetServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final ProxyNetServerDecoder decodeData = this::decodeData;

    private final ProxyRouter router;
    private final MutableDirectBuffer writeBuffer;
    private final BufferPool decodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;

    private final DirectBuffer headerRO = EMPTY_BUFFER;

    public ProxyServerFactory(
        ProxyConfiguration config,
        EngineContext context)
    {
        this.router = new ProxyRouter(context.supplyTypeId(ProxyBinding.NAME));
        this.writeBuffer = context.writeBuffer();
        this.decodePool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        ProxyBindingConfig proxyBinding = new ProxyBindingConfig(binding);
        router.attach(proxyBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        router.detach(bindingId);
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
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();

        MessageConsumer newStream = null;

        final ProxyBindingConfig binding = router.lookup(routeId);
        if (binding != null)
        {
            newStream = new ProxyNetServer(routeId, initialId, sender, affinity)::onNetMessage;
        }

        return newStream;
    }

    private final class ProxyNetServer
    {
        private final MessageConsumer receiver;
        private final long routeId;
        private final long initialId;
        private final long affinity;
        private final long replyId;

        private ProxyNetServerDecoder decoder;
        private int decodeSlot = NO_SLOT;
        private int decodeOffset;
        private int decodeLimit;
        private int decodeReserved;
        private int decodeFlags;

        private ProxyAddrFamily decodedFamily;
        private ProxyAddrProtocol decodedTransport;
        private long decodedCrc32c = -1L;
        private CRC32C crc32c;
        private int decodableBytes;
        private int decodableTlvBytes;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private ProxyAppServer app;

        private ProxyNetServer(
            long routeId,
            long initialId,
            MessageConsumer receiver,
            long affinity)
        {
            this.routeId = routeId;
            this.initialId = initialId;
            this.receiver = receiver;
            this.affinity = affinity;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.decoder = decodeHeader;
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
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final OctetsFW extension = begin.extension();
            final ProxyBeginExFW beginEx = extension.get(beginExRO::tryWrap);

            state = ProxyState.openedInitial(state);
            crc32c = new CRC32C();

            if (beginEx != null)
            {
                decodeSlot = decodePool.acquire(initialId);
                assert decodeSlot != NO_SLOT;

                final MutableDirectBuffer decodeBuf = decodePool.buffer(decodeSlot);
                decodeBuf.putBytes(0, beginEx.buffer(), beginEx.offset(), beginEx.sizeof());

                decodeOffset = beginEx.sizeof();
                decodeLimit = decodeOffset;
            }

            doNetWindow(traceId, authorization, 0L, 0, 0, 0, 0, 16);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doNetReset(traceId, authorization);
                if (app != null)
                {
                    app.doAppAbort(traceId, authorization);
                }
            }
            else
            {
                MutableDirectBuffer buffer = (MutableDirectBuffer) payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();
                int flags = data.flags();

                if (decodeLimit != decodeOffset)
                {
                    assert decodeSlot != NO_SLOT;
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(decodeLimit, buffer, offset, limit - offset);
                    decodeLimit += limit - offset;
                    decodeFlags |= flags;

                    buffer = decodeBuffer;
                    offset = decodeOffset;
                    limit = decodeLimit;
                    reserved = decodeReserved;
                    flags = decodeFlags;
                }

                decodeNet(traceId, authorization, flags, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = ProxyState.closedInitial(state);

            if (app != null)
            {
                app.doAppEnd(traceId, authorization);
            }
            else
            {
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = ProxyState.closedInitial(state);

            if (app != null)
            {
                app.doAppAbort(traceId, authorization);
            }
            else
            {
                doNetAbort(traceId, authorization);
            }
        }

        private void onNetFlush(
            FlushFW flush)
        {
            if (app != null)
            {
                final long traceId = flush.traceId();
                final long authorization = flush.authorization();
                final long budgetId = flush.budgetId();
                final int reserved = flush.reserved();

                app.doAppFlush(traceId, authorization, budgetId, reserved);
            }
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
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            state = ProxyState.openedReply(state);

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;

            assert replyAck <= replySeq;

            if (app != null)
            {
                final int replyWin = replyMax - (int)(replySeq - replyAck);
                if (replyWin > 0)
                {
                    app.doAppWindow(traceId, authorization, budgetId, minimum, capabilities, replyWin, replyPad, replyMax);
                }
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = ProxyState.closedReply(state);

            if (app != null)
            {
                app.doAppReset(traceId, authorization);
            }
            else
            {
                doNetReset(traceId, authorization);
            }
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            if (app != null)
            {
                app.doAppChallenge(traceId, authorization, extension);
            }
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity)
        {
            doBegin(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, EMPTY_OCTETS);
            state = ProxyState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload)
        {
            doData(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, budgetId, reserved, payload);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!ProxyState.replyClosed(state))
            {
                doEnd(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = ProxyState.closedReply(state);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            if (ProxyState.replyOpening(state) && !ProxyState.replyClosed(state))
            {
                doAbort(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = ProxyState.closedReply(state);
            }
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId, reserved);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!ProxyState.initialClosed(state))
            {
                doReset(receiver, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = ProxyState.closedInitial(state);
            }
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            int minInitialWin,
            int minInitialPad,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialWin, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax)
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                doWindow(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, minInitialPad, minimum, capabilities);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }

        private void decodeNet(
            long traceId,
            long authorization,
            long budgetId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = decodePool.buffer(decodeSlot);
                final int offset = decodeOffset;
                final int limit = decodeLimit;
                final int reserved = decodeReserved;
                final int flags = decodeFlags;

                decodeNet(traceId, authorization, flags, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void decodeNet(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int limit)
        {
            ProxyNetServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, flags, budgetId,
                        reserved, buffer, offset, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = decodePool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanup(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer decodeBuffer = decodePool.buffer(decodeSlot);
                    decodeBuffer.putBytes(0, buffer, progress, limit - progress);
                    decodeLimit = decodeOffset + limit - progress;
                    decodeReserved = (limit - progress) * reserved / (limit - offset);
                }
            }
            else
            {
                cleanupDecodeSlot(false);

                if (ProxyState.initialClosing(state) && app != null)
                {
                    app.doAppEnd(traceId, authorization);
                }
            }
        }

        private void onNetReady(
            long traceId,
            long authorization)
        {
            final DirectBuffer decodeBuffer = decodeSlot != NO_SLOT ? decodePool.buffer(decodeSlot) : EMPTY_BUFFER;

            final ProxyBeginExFW beginEx = beginExRO.tryWrap(decodeBuffer, 0, decodeOffset);

            final ProxyBindingConfig binding = router.lookup(routeId);
            final ProxyRouteConfig resolved = binding != null ? binding.resolve(authorization, beginEx) : null;
            if (resolved != null)
            {
                app = new ProxyAppServer(this, resolved.id);
                app.doAppBegin(traceId, authorization, affinity, beginEx != null ? beginEx : EMPTY_OCTETS);
            }
            else
            {
                cleanup(traceId, authorization);
            }
        }

        private void cleanupDecodeSlot(
            boolean force)
        {
            if (decodeSlot != NO_SLOT && (app != null || force))
            {
                decodePool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeOffset = 0;
                decodeLimit = 0;
                decodeReserved = 0;
                decodeFlags = 0;
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            cleanupDecodeSlot(true);
            doNetReset(traceId, authorization);
            doNetAbort(traceId, authorization);
            if (app != null)
            {
                app.cleanup(traceId, authorization);
            }
            decoder = decodeIgnoreAll;
        }
    }

    private final class ProxyAppServer
    {
        private final ProxyNetServer net;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private MessageConsumer receiver;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private ProxyAppServer(
            ProxyNetServer net,
            long routeId)
        {
            this.net = net;
            this.routeId = routeId;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId =  supplyReplyId.applyAsLong(initialId);
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
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            state = ProxyState.openedReply(state);

            net.doNetBegin(traceId, authorization, affinity);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long authorization = data.authorization();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + data.reserved();

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                doAppReset(traceId, authorization);
                net.doNetAbort(traceId, authorization);
            }
            else
            {
                net.doNetData(traceId, authorization, flags, budgetId, reserved, payload);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            state = ProxyState.closedReply(state);

            net.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            state = ProxyState.closedReply(state);

            net.doNetAbort(traceId, authorization);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            net.doNetFlush(traceId, authorization, budgetId, reserved);
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
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            state = ProxyState.openedInitial(state);

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;

            assert initialAck <= initialSeq;

            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            if (initialWin > 0)
            {
                net.decodeNet(traceId, authorization, budgetId);
                net.doNetWindow(traceId, authorization, budgetId, minimum, capabilities, initialWin, initialPad, initialMax);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            state = ProxyState.closedInitial(state);

            net.doNetReset(traceId, authorization);
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            net.doNetChallenge(traceId, authorization, extension);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity,
            Flyweight extension)
        {
            receiver = newStream(this::onAppMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, extension);
            state = ProxyState.openingInitial(state);
        }

        private void doAppData(
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload)
        {
            doData(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload);

            initialSeq += reserved;

            assert initialAck <= initialSeq;
        }

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            if (!ProxyState.initialClosed(state))
            {
                doEnd(receiver, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = ProxyState.closedInitial(state);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            if (!ProxyState.initialClosed(state))
            {
                doAbort(receiver, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                state = ProxyState.closedInitial(state);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(receiver, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, budgetId, reserved);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!ProxyState.replyClosed(state))
            {
                doReset(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                state = ProxyState.closedReply(state);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            int minReplyWin,
            int minReplyPad,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyWin, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax)
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                doWindow(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId,
                        minReplyPad, minimum, capabilities);
            }
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(receiver, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            doAppReset(traceId, authorization);
            doAppAbort(traceId, authorization);
        }
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long affinity,
        Flyweight extension)
    {
        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        OctetsFW payload)
    {
        DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                .payload(payload)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
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

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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
        int minimum,
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
                .minimum(minimum)
                .capabilities(capabilities)
                .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doChallenge(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        receiver.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
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

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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

    private int decodeHeader(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= HEADER_V2_SIZE)
        {
            int anchor = progress;
            DirectBuffer header = headerRO;
            header.wrap(buffer, progress, HEADER_V2_SIZE);
            if (!HEADER_V2.equals(header))
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            progress += HEADER_V2_SIZE;

            updateCRC32C(net.crc32c, buffer, anchor, progress - anchor);

            net.decoder = decodeVersion;
        }

        return progress;
    }

    private int decodeVersion(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length > 0)
        {
            int version = (buffer.getByte(progress) >> 4) & 0x0f;

            if (version != 2)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            net.decoder = decodeCommand;
        }

        return progress;
    }

    private int decodeCommand(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length > 0)
        {
            int anchor = progress;
            int command = buffer.getByte(progress) & 0x0f;

            progress++;

            updateCRC32C(net.crc32c, buffer, anchor, progress - anchor);

            switch (command)
            {
            case 0:
                net.decoder = decodeLocal;
                break;
            case 1:
                net.decoder = decodeProxy;
                break;
            default:
                net.cleanup(traceId, authorization);
                break decode;
            }
        }

        return progress;
    }

    private int decodeLocal(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= Byte.BYTES + Short.BYTES)
        {
            int anchor = progress;
            int transport = buffer.getByte(progress) & 0x0f;

            if (transport > 3)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }
            progress += Byte.BYTES;

            int remaining = buffer.getShort(progress, BIG_ENDIAN) & 0xffff;

            progress += Short.BYTES;

            updateCRC32C(net.crc32c, buffer, anchor, progress - anchor);

            if (remaining == 0)
            {
                net.onNetReady(traceId, authorization);
                net.decoder = decodeData;
            }
            else
            {
                net.doNetWindow(traceId, authorization, budgetId, 0, 0, 0, 0, remaining);

                net.decodableBytes = remaining;
                net.decoder = decodeIgnore;
            }
        }

        return progress;
    }

    private int decodeIgnore(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0 || net.decodableBytes == 0)
        {
            int remaining = Math.min(length, net.decodableBytes);

            progress += remaining;
            net.decodableBytes -= remaining;

            if (net.decodableBytes == 0)
            {
                net.onNetReady(traceId, authorization);
                net.decoder = decodeData;
            }
        }

        return progress;
    }


    private int decodeIgnoreAll(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private int decodeProxy(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= Byte.BYTES + Short.BYTES)
        {
            int anchor = progress;
            byte protocol = buffer.getByte(progress);
            int family = (protocol >> 4) & 0x0f;
            int transport = protocol & 0x0f;

            if (family == 0 || family > 3 || transport == 0 || transport > 2)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }
            progress += Byte.BYTES;

            int remaining = buffer.getShort(progress, BIG_ENDIAN) & 0xffff;

            net.doNetWindow(traceId, authorization, budgetId, 0, 0, 0, 0, remaining);

            net.decodedFamily = ProxyAddrFamily.valueOf(family);
            net.decodedTransport = ProxyAddrProtocol.valueOf(transport);
            net.decodableBytes = remaining;

            progress += Short.BYTES;

            updateCRC32C(net.crc32c, buffer, anchor, progress - anchor);

            switch (net.decodedFamily)
            {
            case INET4:
                if (remaining < PROXY_ADDRESS_LENGTH_INET4)
                {
                    net.cleanup(traceId, authorization);
                    break decode;
                }
                net.decoder = decodeProxyInet4;
                break;
            case INET6:
                if (remaining < PROXY_ADDRESS_LENGTH_INET6)
                {
                    net.cleanup(traceId, authorization);
                    break decode;
                }
                net.decoder = decodeProxyInet6;
                break;
            case UNIX:
                if (remaining < PROXY_ADDRESS_LENGTH_UNIX)
                {
                    net.cleanup(traceId, authorization);
                    break decode;
                }
                net.decoder = decodeProxyUnix;
                break;
            }
        }

        return progress;
    }

    private int decodeProxyInet4(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= PROXY_ADDRESS_LENGTH_INET4)
        {
            ProxyAddrInet4FW addressInet4 = addressInet4RO.tryWrap(buffer, progress, limit);

            if (addressInet4 == null)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            final OctetsFW source = addressInet4.source();
            final OctetsFW destination = addressInet4.destination();
            final int sourcePort = addressInet4.sourcePort();
            final int destinationPort = addressInet4.destinationPort();

            if (net.decodeSlot == NO_SLOT)
            {
                net.decodeSlot = decodePool.acquire(net.initialId);
            }
            assert net.decodeSlot != NO_SLOT;

            final MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            decodeBuf.putInt(net.decodeOffset, router.typeId());
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            ProxyAddressFW address = addressRW
                    .wrap(decodeBuf, net.decodeOffset, decodeBuf.capacity())
                    .inet4(i -> i.protocol(t -> t.set(ProxyAddressProtocol.valueOf(net.decodedTransport.ordinal())))
                                 .source(source)
                                 .destination(destination)
                                 .sourcePort(sourcePort)
                                 .destinationPort(destinationPort))
                    .build();

            net.decodableBytes -= addressInet4.sizeof();
            net.decodeOffset += address.sizeof();
            net.decodeLimit = net.decodeOffset;
            progress = addressInet4.limit();

            decodeBuf.putInt(net.decodeOffset, Integer.BYTES);
            net.decodeOffset += Integer.BYTES;
            decodeBuf.putInt(net.decodeOffset, 0);
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            updateCRC32C(net.crc32c, addressInet4.buffer(), addressInet4.offset(), addressInet4.sizeof());

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyInet6(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= PROXY_ADDRESS_LENGTH_INET6)
        {
            ProxyAddrInet6FW addressInet6 = addressInet6RO.tryWrap(buffer, progress, limit);

            if (addressInet6 == null)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            final OctetsFW source = addressInet6.source();
            final OctetsFW destination = addressInet6.destination();
            final int sourcePort = addressInet6.sourcePort();
            final int destinationPort = addressInet6.destinationPort();

            if (net.decodeSlot == NO_SLOT)
            {
                net.decodeSlot = decodePool.acquire(net.initialId);
            }
            assert net.decodeSlot != NO_SLOT;

            final MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            decodeBuf.putInt(net.decodeOffset, router.typeId());
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            ProxyAddressFW address = addressRW
                    .wrap(decodeBuf, net.decodeOffset, decodeBuf.capacity())
                    .inet6(i -> i.protocol(t -> t.set(ProxyAddressProtocol.valueOf(net.decodedTransport.ordinal())))
                                 .source(source)
                                 .destination(destination)
                                 .sourcePort(sourcePort)
                                 .destinationPort(destinationPort))
                    .build();

            net.decodableBytes -= addressInet6.sizeof();
            net.decodeOffset += address.sizeof();
            net.decodeLimit = net.decodeOffset;
            progress = addressInet6.limit();

            decodeBuf.putInt(net.decodeOffset, Integer.BYTES);
            net.decodeOffset += Integer.BYTES;
            decodeBuf.putInt(net.decodeOffset, 0);
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            updateCRC32C(net.crc32c, addressInet6.buffer(), addressInet6.offset(), addressInet6.sizeof());

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyUnix(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length >= PROXY_ADDRESS_LENGTH_UNIX)
        {
            ProxyAddrUnixFW addressUnix = addressUnixRO.tryWrap(buffer, progress, limit);

            if (addressUnix == null)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            OctetsFW source = addressUnix.source();
            OctetsFW destination = addressUnix.destination();

            if (net.decodeSlot == NO_SLOT)
            {
                net.decodeSlot = decodePool.acquire(net.initialId);
            }
            assert net.decodeSlot != NO_SLOT;

            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            decodeBuf.putInt(net.decodeOffset, router.typeId());
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            ProxyAddressFW address = addressRW
                    .wrap(decodeBuf, net.decodeOffset, decodeBuf.capacity())
                    .unix(i -> i.protocol(t -> t.set(ProxyAddressProtocol.valueOf(net.decodedTransport.ordinal())))
                                .source(source)
                                .destination(destination))
                    .build();

            net.decodableBytes -= addressUnix.sizeof();
            net.decodeOffset += address.sizeof();
            net.decodeLimit = net.decodeOffset;
            progress = addressUnix.limit();

            decodeBuf.putInt(net.decodeOffset, Integer.BYTES);
            net.decodeOffset += Integer.BYTES;
            decodeBuf.putInt(net.decodeOffset, 0);
            net.decodeOffset += Integer.BYTES;
            net.decodeLimit = net.decodeOffset;

            updateCRC32C(net.crc32c, addressUnix.buffer(), addressUnix.offset(), addressUnix.sizeof());

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlv(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (net.decodableBytes == 0)
        {
            if (net.decodedCrc32c != -1L && net.decodedCrc32c != net.crc32c.getValue())
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            net.crc32c = null;

            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            net.decodeOffset += size - Integer.BYTES;
            net.decodeLimit = net.decodeOffset;
            net.onNetReady(traceId, authorization);
            net.decoder = decodeData;
        }
        else if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.tryWrap(buffer, progress, limit);

            if (tlv != null)
            {
                switch (tlv.type())
                {
                case 0x01:
                    net.decoder = decodeProxyTlvAlpn;
                    break;
                case 0x02:
                    net.decoder = decodeProxyTlvAuthority;
                    break;
                case 0x03:
                    net.decoder = decodeProxyTlvCrc32c;
                    break;
                case 0x04:
                    net.decoder = decodeProxyTlvIgnore;
                    break;
                case 0x05:
                    net.decoder = decodeProxyTlvUniqueId;
                    break;
                case 0x20:
                    net.decoder = decodeProxyTlvSsl;
                    break;
                case 0x30:
                    net.decoder = decodeProxyTlvNetns;
                    break;
                default:
                    net.decoder = decodeProxyTlvIgnore;
                    break;
                }
            }
        }

        return progress;
    }

    private int decodeProxyTlvAlpn(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW alpn = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .alpn(alpn)
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlvAuthority(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW authority = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .authority(authority)
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlvCrc32c(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        MutableDirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            if (tlv.length() != Integer.BYTES)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            net.decodedCrc32c = tlv.value().value().getInt(0, BIG_ENDIAN) & 0xffff_ffffL;

            buffer.putInt(tlv.offset() + ProxyTlvFW.FIELD_OFFSET_VALUE, 0);
            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlvIgnore(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlvUniqueId(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW uniqueId = tlv.value();
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .identity(i -> i.value(uniqueId))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSsl(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            ProxyTlvSslFW ssl = tlv.value().get(tlvSslRO::tryWrap);
            if (ssl == null)
            {
                net.cleanup(traceId, authorization);
                break decode;
            }

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), ssl.limit() - tlv.offset());

            net.decodableBytes -= ssl.limit() - tlv.offset();
            net.decodableTlvBytes = tlv.length() - ssl.sizeof();
            progress += ssl.limit() - tlv.offset();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslSubTlv(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        decode:
        if (net.decodableTlvBytes == 0)
        {
            net.decoder = decodeProxyTlv;
        }
        else if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.tryWrap(buffer, progress, limit);
            if (tlv == null)
            {
                break decode;
            }

            switch (tlv.type())
            {
            case 0x21:
                net.decoder = decodeProxyTlvSslVersion;
                break;
            case 0x22:
                net.decoder = decodeProxyTlvSslCommonName;
                break;
            case 0x23:
                net.decoder = decodeProxyTlvSslCipher;
                break;
            case 0x24:
                net.decoder = decodeProxyTlvSslSignature;
                break;
            case 0x25:
                net.decoder = decodeProxyTlvSslKey;
                break;
            default:
                net.decoder = decodeProxyTlvSslSubTlvIgnore;
                break;
            }
        }

        return progress;
    }

    private int decodeProxyTlvSslVersion(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW version = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .secure(s -> s.version(version))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslCommonName(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW commonName = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .secure(s -> s.name(commonName))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslCipher(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW cipher = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .secure(s -> s.cipher(cipher))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslSignature(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW signature = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .secure(s -> s.signature(signature))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslKey(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW key = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .secure(s -> s.key(key))
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvSslSubTlvIgnore(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableTlvBytes -= tlv.sizeof();
            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlvSslSubTlv;
        }

        return progress;
    }

    private int decodeProxyTlvNetns(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            assert net.decodeSlot != NO_SLOT;
            MutableDirectBuffer decodeBuf = decodePool.buffer(net.decodeSlot);
            int size = decodeBuf.getInt(net.decodeOffset - Integer.BYTES - Integer.BYTES);
            int items = decodeBuf.getInt(net.decodeOffset - Integer.BYTES);

            ProxyTlvFW tlv = tlvRO.wrap(buffer, progress, limit);
            OctetsFW tlvBounded = tlvBoundedRO.wrap(tlv.buffer(), tlv.offset() + ProxyTlvFW.FIELD_OFFSET_LENGTH, tlv.limit());
            String16FW namespace = tlvBounded.get(tlvStringRO::wrap);
            ProxyInfoFW info = infoRW.wrap(decodeBuf, net.decodeOffset + size - Integer.BYTES, decodePool.slotCapacity())
                    .namespace(namespace)
                    .build();

            size += info.sizeof();
            items++;

            decodeBuf.putInt(net.decodeOffset - Integer.BYTES - Integer.BYTES, size);
            decodeBuf.putInt(net.decodeOffset - Integer.BYTES, items);

            updateCRC32C(net.crc32c, tlv.buffer(), tlv.offset(), tlv.sizeof());

            net.decodableBytes -= tlv.sizeof();
            progress += tlv.sizeof();

            net.decoder = decodeProxyTlv;
        }

        return progress;
    }

    private int decodeData(
        ProxyNetServer net,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int offset,
        int progress,
        int limit)
    {
        int length = limit - progress;

        if (length > 0)
        {
            OctetsFW payload = payloadRO.wrap(buffer, progress, limit);
            net.app.doAppData(traceId, authorization, budgetId, flags, reserved, payload);
            progress += length;
        }

        return progress;
    }

    private static void updateCRC32C(
        CRC32C crc32c,
        DirectBuffer buffer,
        int index,
        int length)
    {
        ByteBuffer buf = buffer.byteBuffer();
        int position = buf.position();
        int limit = buf.limit();
        buf.clear().position(index).limit(index + length);
        crc32c.update(buf);
        buf.clear().position(position).limit(limit);
    }

    @FunctionalInterface
    private interface ProxyNetServerDecoder
    {
        int decode(
            ProxyNetServer net,
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            MutableDirectBuffer buffer,
            int offset,
            int progress,
            int limit);
    }
}
