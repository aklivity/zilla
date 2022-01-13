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

import static io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFamily.INET;
import static io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFamily.INET4;
import static io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFamily.INET6;
import static io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.cog.proxy.internal.ProxyBinding;
import io.aklivity.zilla.runtime.cog.proxy.internal.ProxyConfiguration;
import io.aklivity.zilla.runtime.cog.proxy.internal.config.ProxyBindingConfig;
import io.aklivity.zilla.runtime.cog.proxy.internal.config.ProxyRouteConfig;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.Array32FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressFamily;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressInet4FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressInet6FW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressUnixFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxySecureInfoFW;
import io.aklivity.zilla.runtime.cog.proxy.internal.types.codec.ProxyTlvFW;
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

public final class ProxyClientFactory implements ProxyStreamFactory
{
    private static final InetAddress INET4_ANY_LOCAL_ADDRESS = getInetAddressByAddress(new byte[4]);
    private static final InetAddress INET6_ANY_LOCAL_ADDRESS = getInetAddressByAddress(new byte[16]);

    private static final DirectBuffer HEADER_V2 = new UnsafeBuffer("\r\n\r\n\0\r\nQUIT\n".getBytes(US_ASCII));

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ChallengeFW challengeRO = new ChallengeFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final ProxyBeginExFW beginExRO = new ProxyBeginExFW();

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final OctetsFW payloadRO = new OctetsFW();

    private final ProxyInfoFW infoRO = new ProxyInfoFW();
    private final ProxyTlvFW.Builder tlvRW = new ProxyTlvFW.Builder();

    private final ProxyRouter router;
    private final MutableDirectBuffer writeBuffer;
    private final BufferPool encodePool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final Function<String, InetAddress[]> resolveHost;

    public ProxyClientFactory(
        ProxyConfiguration config,
        EngineContext context)
    {
        this.router = new ProxyRouter(context.supplyTypeId(ProxyBinding.NAME));
        this.writeBuffer = context.writeBuffer();
        this.encodePool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.resolveHost = context::resolveHost;
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
        final long authorization = begin.authorization();
        final ProxyBeginExFW beginEx = begin.extension().get(beginExRO::tryWrap);

        MessageConsumer newStream = null;

        final ProxyBindingConfig binding = router.lookup(routeId);
        final ProxyRouteConfig resolved = binding != null ? binding.resolve(authorization, beginEx) : null;
        if (resolved != null)
        {
            newStream = new ProxyAppClient(routeId, initialId, sender, resolved.id)::onAppMessage;
        }

        return newStream;
    }

    private final class ProxyAppClient
    {
        private final MessageConsumer receiver;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private final ProxyNetClient net;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;

        private ProxyAppClient(
            long routeId,
            long initialId,
            MessageConsumer receiver,
            long resolvedId)
        {
            this.routeId = routeId;
            this.initialId = initialId;
            this.receiver = receiver;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.net = new ProxyNetClient(this, resolvedId);
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
            final OctetsFW extension = begin.extension();

            final ProxyBeginExFW beginEx = extension.get(beginExRO::tryWrap);

            net.doNetBegin(traceId, authorization, affinity, beginEx);
        }

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doAppReset(traceId, authorization);
                net.doNetAbort(traceId, authorization);
            }
            else
            {
                net.doNetData(traceId, authorization, budgetId, flags, reserved, payload);
            }
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            net.doNetEnd(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

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

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;

            assert replyAck <= replySeq;

            final int replyWin = replyMax - (int)(replySeq - replyAck);
            if (replyWin > 0)
            {
                net.doNetWindow(traceId, authorization, budgetId, minimum, capabilities, replyWin, replyPad, replyMax);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

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
            long affinity)
        {
            doBegin(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity);
        }

        private void doAppData(
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

        private void doAppEnd(
            long traceId,
            long authorization)
        {
            doEnd(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
        }

        private void doAppAbort(
            long traceId,
            long authorization)
        {
            doAbort(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            doReset(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doAppWindow(
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

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }
    }

    private final class ProxyNetClient
    {
        private final ProxyAppClient app;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private MessageConsumer receiver;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private ProxyNetClient(
            ProxyAppClient application,
            long routeId)
        {
            this.app = application;
            this.routeId = routeId;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId =  supplyReplyId.applyAsLong(initialId);
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
            final long affinity = begin.affinity();

            app.doAppBegin(traceId, authorization, affinity);
        }

        private void onNetData(
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
                doNetReset(traceId, authorization);
                app.doAppAbort(traceId, authorization);
            }
            else
            {
                app.doAppData(traceId, authorization, flags, budgetId, reserved, payload);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            app.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            app.doAppAbort(traceId, authorization);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            app.doAppFlush(traceId, authorization, budgetId, reserved);
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
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum >= initialMax;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;

            assert initialAck <= initialSeq;

            if (encodeSlot != NO_SLOT)
            {
                DirectBuffer encodeBuffer = encodePool.buffer(encodeSlot);
                OctetsFW payload = payloadRO.wrap(encodeBuffer, 0, encodeSlotOffset);

                doNetData(traceId, authorization, budgetId, 0x03, payload.sizeof() + padding, payload);

                encodePool.release(encodeSlot);
                encodeSlot = NO_SLOT;
            }

            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            if (initialWin > 0)
            {
                app.doAppWindow(traceId, authorization, budgetId, minimum, capabilities, initialWin, initialPad, initialMax);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            app.doAppReset(traceId, authorization);
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            final long traceId = challenge.traceId();
            final long authorization = challenge.authorization();
            final OctetsFW extension = challenge.extension();

            app.doAppChallenge(traceId, authorization, extension);
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            ProxyBeginExFW beginEx)
        {
            assert encodeSlot == NO_SLOT;
            encodeSlot = encodePool.acquire(initialId);
            assert encodeSlot != NO_SLOT;

            MutableDirectBuffer buffer = encodePool.buffer(encodeSlot);
            if (beginEx != null)
            {
                encodeSlotOffset = encodeProxy(buffer, beginEx);
            }
            else
            {
                encodeSlotOffset = encodeLocal(buffer);
            }

            receiver = newStream(this::onNetMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity);
        }

        private void doNetData(
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

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            doEnd(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
            doAbort(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved)
        {
            doFlush(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved);
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            doReset(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, extension);
        }

        private void doNetWindow(
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

        private int encodeHeader(
            MutableDirectBuffer buffer)
        {
            buffer.putBytes(0, HEADER_V2, 0, HEADER_V2.capacity());
            return HEADER_V2.capacity();
        }

        private int encodeLocal(
            MutableDirectBuffer buffer)
        {
            int progress = encodeHeader(buffer);

            buffer.putByte(progress++, (byte) 0x20);
            buffer.putByte(progress++, (byte) 0x00);
            buffer.putByte(progress++, (byte) 0x00);
            buffer.putByte(progress++, (byte) 0x00);
            return progress;
        }

        private int encodeProxy(
            MutableDirectBuffer buffer,
            ProxyBeginExFW beginEx)
        {
            ProxyAddressFW address = beginEx.address();
            Array32FW<ProxyInfoFW> infos = beginEx.infos();

            int progress = encodeHeader(buffer);

            buffer.putByte(progress++, (byte) 0x21);

            progress = encodeProxyAddress(buffer, progress, address);
            progress = encodeProxyTlvs(buffer, progress, infos);

            buffer.putShort(14, (short) (progress - 14 - Short.BYTES), BIG_ENDIAN);
            return progress;
        }

        private int encodeProxyAddress(
            MutableDirectBuffer buffer,
            int progress,
            ProxyAddressFW address)
        {
            switch (address.kind())
            {
            case INET:
                progress = encodeProxyAddressInet(buffer, progress, address);
                break;
            case INET4:
                progress = encodeProxyAddressInet4(buffer, progress, address);
                break;
            case INET6:
                progress = encodeProxyAddressInet6(buffer, progress, address);
                break;
            case UNIX:
                progress = encodeProxyAddressUnix(buffer, progress, address);
                break;
            case NONE:
                break;
            }
            return progress;
        }

        private int encodeProxyAddressInet(
            MutableDirectBuffer buffer,
            int progress,
            ProxyAddressFW address)
        {
            ProxyAddressInetFW inet = address.inet();
            String sourceName = inet.source().asString();
            String destinationName = inet.destination().asString();

            InetAddress destinationInet = resolveHost.apply(destinationName)[0];
            byte[] destination = destinationInet.getAddress();
            ProxyAddressFamily family = asProxyAddressFamily(destinationInet);
            assert family == INET4 || family == INET6;
            InetAddress sourceInet = sourceName != null ? resolveHost.apply(sourceName)[0] : getInetAddressLocal(family);
            byte[] source = sourceInet.getAddress();
            assert asProxyAddressFamily(sourceInet) == family;

            buffer.putByte(progress++, (byte) ((family.ordinal() << 4) | (inet.protocol().get().ordinal() + 1)));
            progress += Short.BYTES;
            buffer.putBytes(progress, source, 0, source.length);
            progress += source.length;
            buffer.putBytes(progress, destination, 0, destination.length);
            progress += destination.length;
            buffer.putShort(progress, (short) inet.sourcePort(), BIG_ENDIAN);
            progress += Short.BYTES;
            buffer.putShort(progress, (short) inet.destinationPort(), BIG_ENDIAN);
            progress += Short.BYTES;
            return progress;
        }

        private int encodeProxyAddressInet4(
            MutableDirectBuffer buffer,
            int progress,
            ProxyAddressFW address)
        {
            ProxyAddressInet4FW inet4 = address.inet4();
            buffer.putByte(progress++, (byte) (0x10 | (inet4.protocol().get().ordinal() + 1)));
            progress += Short.BYTES;
            buffer.putBytes(progress, inet4.source().value(), 0, inet4.source().sizeof());
            progress += inet4.source().sizeof();
            buffer.putBytes(progress, inet4.destination().value(), 0, inet4.destination().sizeof());
            progress += inet4.destination().sizeof();
            buffer.putShort(progress, (short) inet4.sourcePort(), BIG_ENDIAN);
            progress += Short.BYTES;
            buffer.putShort(progress, (short) inet4.destinationPort(), BIG_ENDIAN);
            progress += Short.BYTES;
            return progress;
        }

        private int encodeProxyAddressInet6(
            MutableDirectBuffer buffer,
            int progress,
            ProxyAddressFW address)
        {
            ProxyAddressInet6FW inet6 = address.inet6();
            buffer.putByte(progress++, (byte) (0x20 | (inet6.protocol().get().ordinal() + 1)));
            progress += Short.BYTES;
            buffer.putBytes(progress, inet6.source().value(), 0, inet6.source().sizeof());
            progress += inet6.source().sizeof();
            buffer.putBytes(progress, inet6.destination().value(), 0, inet6.destination().sizeof());
            progress += inet6.destination().sizeof();
            buffer.putShort(progress, (short) inet6.sourcePort(), BIG_ENDIAN);
            progress += Short.BYTES;
            buffer.putShort(progress, (short) inet6.destinationPort(), BIG_ENDIAN);
            progress += Short.BYTES;
            return progress;
        }

        private int encodeProxyAddressUnix(
            MutableDirectBuffer buffer,
            int progress,
            ProxyAddressFW address)
        {
            ProxyAddressUnixFW unix = address.unix();
            buffer.putByte(progress++, (byte) (0x30 | (unix.protocol().get().ordinal() + 1)));
            progress += Short.BYTES;
            buffer.putBytes(progress, unix.source().value(), 0, unix.source().sizeof());
            progress += unix.source().sizeof();
            buffer.putBytes(progress, unix.destination().value(), 0, unix.destination().sizeof());
            progress += unix.destination().sizeof();
            return progress;
        }

        private int encodeProxyTlvs(
            MutableDirectBuffer buffer,
            int progress,
            Array32FW<ProxyInfoFW> infos)
        {
            DirectBuffer items = infos.items();
            for (int itemOffset = 0; itemOffset < items.capacity(); )
            {
                ProxyInfoFW info = infoRO.wrap(items, itemOffset, items.capacity());
                switch (info.kind())
                {
                case ALPN:
                    progress = encodeProxyTlvAlpn(buffer, progress, info);
                    itemOffset = info.limit();
                    break;
                case AUTHORITY:
                    progress = encodeProxyTlvAuthority(buffer, progress, info);
                    itemOffset = info.limit();
                    break;
                case IDENTITY:
                    progress = encodeProxyTlvUniqueId(buffer, progress, info);
                    itemOffset = info.limit();
                    break;
                case SECURE:
                    buffer.putByte(progress++, (byte) 0x20);
                    int secureInfoOffset = progress;
                    progress += Short.BYTES;
                    buffer.putByte(progress, (byte) 0x07);
                    progress += Byte.BYTES;
                    buffer.putInt(progress, 0, BIG_ENDIAN);
                    progress += Integer.BYTES;
                    while (itemOffset < items.capacity() && info.kind() == SECURE)
                    {
                        info = infoRO.wrap(items, itemOffset, items.capacity());
                        ProxySecureInfoFW secureInfo = info.secure();
                        switch (secureInfo.kind())
                        {
                        case VERSION:
                            progress = encodeProxyTlvSslVersion(buffer, progress, secureInfo);
                            break;
                        case NAME:
                            progress = encodeProxyTlvSslCommonName(buffer, progress, secureInfo);
                            break;
                        case CIPHER:
                            progress = encodeProxyTlvSslCipher(buffer, progress, secureInfo);
                            break;
                        case SIGNATURE:
                            progress = encodeProxyTlvSslSignature(buffer, progress, secureInfo);
                            break;
                        case KEY:
                            progress = encodeProxyTlvSslKey(buffer, progress, secureInfo);
                            break;
                        }
                        itemOffset = info.limit();
                    }

                    buffer.putShort(secureInfoOffset,
                            (short) (progress - secureInfoOffset - Short.BYTES), BIG_ENDIAN);
                    break;
                case NAMESPACE:
                    progress = encodeProxyTlvNamespace(buffer, progress, info);
                    itemOffset = info.limit();
                    break;
                default:
                    itemOffset = info.limit();
                    break;
                }
            }
            return progress;
        }

        private int encodeProxyTlvAlpn(
            MutableDirectBuffer buffer,
            int progress,
            ProxyInfoFW info)
        {
            DirectBuffer alpn = info.alpn().value();
            ProxyTlvFW alpnTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                 .type(0x01)
                 .value(alpn, 0, alpn.capacity())
                 .build();
            progress += alpnTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvAuthority(
            MutableDirectBuffer buffer,
            int progress,
            ProxyInfoFW info)
        {
            DirectBuffer authority = info.authority().value();
            ProxyTlvFW authorityTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                 .type(0x02)
                 .value(authority, 0, authority.capacity())
                 .build();
            progress += authorityTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvUniqueId(
            MutableDirectBuffer buffer,
            int progress,
            ProxyInfoFW info)
        {
            OctetsFW identity = info.identity().value();
            ProxyTlvFW identityTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                 .type(0x05)
                 .value(identity)
                 .build();
            progress += identityTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvSslKey(
            MutableDirectBuffer buffer,
            int progress,
            ProxySecureInfoFW secureInfo)
        {
            DirectBuffer key = secureInfo.key().value();
            ProxyTlvFW keyTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                .type(0x25)
                .value(key, 0, key.capacity())
                .build();
            progress += keyTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvSslSignature(
            MutableDirectBuffer buffer,
            int progress,
            ProxySecureInfoFW secureInfo)
        {
            DirectBuffer signature = secureInfo.signature().value();
            ProxyTlvFW signatureTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                .type(0x24)
                .value(signature, 0, signature.capacity())
                .build();
            progress += signatureTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvSslCipher(
            MutableDirectBuffer buffer,
            int progress,
            ProxySecureInfoFW secureInfo)
        {
            DirectBuffer cipher = secureInfo.cipher().value();
            ProxyTlvFW cipherTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                .type(0x23)
                .value(cipher, 0, cipher.capacity())
                .build();
            progress += cipherTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvSslCommonName(
            MutableDirectBuffer buffer,
            int progress,
            ProxySecureInfoFW secureInfo)
        {
            DirectBuffer commonName = secureInfo.name().value();
            ProxyTlvFW commonNameTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                .type(0x22)
                .value(commonName, 0, commonName.capacity())
                .build();
            progress += commonNameTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvSslVersion(
            MutableDirectBuffer buffer,
            int progress,
            ProxySecureInfoFW secureInfo)
        {
            DirectBuffer version = secureInfo.version().value();
            ProxyTlvFW versionTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                .type(0x21)
                .value(version, 0, version.capacity())
                .build();
            progress += versionTlv.sizeof();
            return progress;
        }

        private int encodeProxyTlvNamespace(
            MutableDirectBuffer buffer,
            int progress,
            ProxyInfoFW info)
        {
            DirectBuffer namespace = info.namespace().value();
            ProxyTlvFW namespaceTlv = tlvRW.wrap(buffer, progress, buffer.capacity())
                 .type(0x30)
                 .value(namespace, 0, namespace.capacity())
                 .build();
            progress += namespaceTlv.sizeof();
            return progress;
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
        long affinity)
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
        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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

    void doData(
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

    void doWindow(
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

    void doEnd(
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

    void doAbort(
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

    private static ProxyAddressFamily asProxyAddressFamily(
        InetAddress address)
    {
        if (address instanceof Inet4Address)
        {
            return INET4;
        }
        else if (address instanceof Inet6Address)
        {
            return INET6;
        }
        else
        {
            return INET;
        }
    }

    private static InetAddress getInetAddressByAddress(
        byte[] addr)
    {
        InetAddress address = null;

        try
        {
            address = InetAddress.getByAddress(addr);
        }
        catch (UnknownHostException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return address;
    }

    private static InetAddress getInetAddressLocal(
        ProxyAddressFamily family)
    {
        InetAddress address = null;
        switch (family)
        {
        case INET4:
            address = INET4_ANY_LOCAL_ADDRESS;
            break;
        case INET6:
            address = INET6_ANY_LOCAL_ADDRESS;
            break;
        default:
            throw new IllegalArgumentException("Unexpected family: " + family);
        }

        return address;
    }
}
