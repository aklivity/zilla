/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.util.Objects.requireNonNull;

import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmBinding;
import io.aklivity.zilla.runtime.binding.llm.internal.LlmConfiguration;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.config.LlmRouteConfig;
import io.aklivity.zilla.runtime.binding.llm.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBeginExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

// Routes purely on LlmBeginEx.dialect/model, both already resolved and populated by the llm(server)
// hop that produced this stream's own begin extension -- unlike llm(server)/llm(client), this never
// decodes or re-encodes request/response content: DATA/FLUSH/END/ABORT/RESET/WINDOW/CHALLENGE relay
// verbatim between the resolved exit and the accepted network stream once the route is picked.
public final class LlmProxyFactory implements LlmStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ChallengeFW challengeRO = new ChallengeFW();
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final MutableDirectBufferEx writeBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final BindingHandler streamFactory;
    private final int llmTypeId;
    private final EngineContext context;
    private final Long2ObjectHashMap<LlmBindingConfig> bindings;

    public LlmProxyFactory(
        LlmConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = requireNonNull(context.writeBuffer());
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.streamFactory = context.streamFactory();
        this.llmTypeId = context.supplyTypeId(LlmBinding.NAME);
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new LlmBindingConfig(binding, context));
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routedId = begin.routedId();
        final long authorization = begin.authorization();

        final LlmBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final OctetsFW extension = begin.extension();
            final LlmBeginExFW llmBeginEx = extension.get(llmBeginExRO::tryWrap);

            if (llmBeginEx != null)
            {
                final String dialect = llmBeginEx.dialect().asString();
                final String model = llmBeginEx.model().asString();

                final LlmRouteConfig route = binding.resolve(authorization, dialect, model);

                if (route != null)
                {
                    newStream = new LlmServer(
                            network,
                            begin.originId(),
                            routedId,
                            begin.streamId(),
                            route.id)::onNetMessage;
                }
            }
        }

        return newStream;
    }

    private final class LlmServer
    {
        private final MessageConsumer network;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long exitId;

        private LlmClient app;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private LlmServer(
            MessageConsumer network,
            long originId,
            long routedId,
            long initialId,
            long exitId)
        {
            this.network = network;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.exitId = exitId;
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
                onNetBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onNetData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onNetEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onNetAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                onNetFlush(flushRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onNetWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onNetReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onNetChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            this.initialSeq = begin.sequence();
            this.initialAck = begin.acknowledge();
            this.state = LlmState.openingInitial(state);

            this.app = new LlmClient(this);
            app.doAppBegin(traceId, authorization, affinity, begin.extension());
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            this.initialSeq = data.sequence() + reserved;

            app.doAppData(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            this.initialSeq = end.sequence();
            this.state = LlmState.closeInitial(state);

            app.doAppEnd(traceId, authorization, end.extension());
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            this.initialSeq = abort.sequence();
            this.state = LlmState.closeInitial(state);

            app.doAppAbort(traceId, authorization, abort.extension());
        }

        private void onNetFlush(
            FlushFW flush)
        {
            app.doAppFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            this.replyAck = window.acknowledge();
            this.replyMax = window.maximum();
            this.state = LlmState.openReply(state);

            app.doAppWindow(traceId, authorization, budgetId, minimum, capabilities, replySeq, replyAck, replyMax, padding);
        }

        private void onNetReset(
            ResetFW reset)
        {
            this.state = LlmState.closeReply(state);

            app.doAppReset(reset.traceId(), reset.authorization());
        }

        private void onNetChallenge(
            ChallengeFW challenge)
        {
            app.doAppChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void doNetBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW extension)
        {
            doBegin(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, affinity, extension);
            this.state = LlmState.openingReply(state);
        }

        private void doNetData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
            this.replySeq += reserved;
        }

        private void doNetEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                doEnd(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doNetAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.replyClosed(state))
            {
                doAbort(network, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doNetFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(network, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            long appReplySeq,
            long appReplyAck,
            int appReplyMax,
            int padding)
        {
            final long newInitialAck = Math.max(appReplySeq - (appReplyMax - (int) (appReplySeq - appReplyAck)), initialAck);

            if (newInitialAck > initialAck || appReplyMax > initialMax)
            {
                this.initialAck = newInitialAck;
                this.initialMax = appReplyMax;

                doWindow(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, padding, minimum, capabilities);
            }
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.initialClosed(state))
            {
                doReset(network, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doNetChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(network, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, extension);
        }
    }

    private final class LlmClient
    {
        private final LlmServer net;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;

        private MessageConsumer app;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        private LlmClient(
            LlmServer net)
        {
            this.net = net;
            this.originId = net.routedId;
            this.routedId = net.exitId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
        }

        private void doAppBegin(
            long traceId,
            long authorization,
            long affinity,
            OctetsFW extension)
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
                .extension(extension)
                .build();

            this.app = streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(),
                this::onAppMessage);
            app.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            this.state = LlmState.openingInitial(state);
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
                onAppBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case DataFW.TYPE_ID:
                onAppData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onAppEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAppAbort(abortRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                onAppFlush(flushRO.wrap(buffer, index, index + length));
                break;
            case WindowFW.TYPE_ID:
                onAppWindow(windowRO.wrap(buffer, index, index + length));
                break;
            case ResetFW.TYPE_ID:
                onAppReset(resetRO.wrap(buffer, index, index + length));
                break;
            case ChallengeFW.TYPE_ID:
                onAppChallenge(challengeRO.wrap(buffer, index, index + length));
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long affinity = begin.affinity();

            this.replySeq = begin.sequence();
            this.replyAck = begin.acknowledge();
            this.state = LlmState.openingReply(state);

            net.doNetBegin(traceId, authorization, affinity, begin.extension());
        }

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            this.replySeq = data.sequence() + reserved;

            net.doNetData(traceId, authorization, flags, budgetId, reserved, data.payload(), data.extension());
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            this.replySeq = end.sequence();
            this.state = LlmState.closeReply(state);

            net.doNetEnd(traceId, authorization, end.extension());
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            this.replySeq = abort.sequence();
            this.state = LlmState.closeReply(state);

            net.doNetAbort(traceId, authorization, abort.extension());
        }

        private void onAppFlush(
            FlushFW flush)
        {
            net.doNetFlush(flush.traceId(), flush.authorization(), flush.budgetId(), flush.reserved(), flush.extension());
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();
            final int capabilities = window.capabilities();

            this.initialAck = window.acknowledge();
            this.initialMax = window.maximum();
            this.state = LlmState.openInitial(state);

            net.doNetWindow(traceId, authorization, budgetId, minimum, capabilities, initialSeq, initialAck, initialMax,
                    padding);
        }

        private void onAppReset(
            ResetFW reset)
        {
            this.state = LlmState.closeInitial(state);

            net.doNetReset(reset.traceId(), reset.authorization());
        }

        private void onAppChallenge(
            ChallengeFW challenge)
        {
            net.doNetChallenge(challenge.traceId(), challenge.authorization(), challenge.extension());
        }

        private void doAppData(
            long traceId,
            long authorization,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, flags, budgetId, reserved, payload, extension);
            this.initialSeq += reserved;
        }

        private void doAppEnd(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialClosed(state))
            {
                doEnd(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doAppAbort(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            if (!LlmState.initialClosed(state))
            {
                doAbort(app, originId, routedId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                this.state = LlmState.closeInitial(state);
            }
        }

        private void doAppFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(app, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, budgetId, reserved, extension);
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minimum,
            int capabilities,
            long netReplySeq,
            long netReplyAck,
            int netReplyMax,
            int padding)
        {
            final long newReplyAck = Math.max(netReplySeq - (netReplyMax - (int) (netReplySeq - netReplyAck)), replyAck);

            if (newReplyAck > replyAck || netReplyMax > replyMax)
            {
                this.replyAck = newReplyAck;
                this.replyMax = netReplyMax;

                doWindow(app, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, budgetId, padding, minimum, capabilities);
            }
        }

        private void doAppReset(
            long traceId,
            long authorization)
        {
            if (!LlmState.replyClosed(state))
            {
                doReset(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization);
                this.state = LlmState.closeReply(state);
            }
        }

        private void doAppChallenge(
            long traceId,
            long authorization,
            OctetsFW extension)
        {
            doChallenge(app, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, authorization, extension);
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
        OctetsFW extension)
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
            .extension(extension)
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
        int flags,
        long budgetId,
        int reserved,
        OctetsFW payload,
        OctetsFW extension)
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
            .payload(payload)
            .extension(extension)
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
        OctetsFW extension)
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
            .extension(extension)
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
        OctetsFW extension)
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
            .extension(extension)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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
        int reserved,
        OctetsFW extension)
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
            .extension(extension)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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
        int minimum,
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
            .minimum(minimum)
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

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doChallenge(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        OctetsFW extension)
    {
        final ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
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
}
