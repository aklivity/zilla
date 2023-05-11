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
package io.aklivity.zilla.runtime.binding.fan.internal.stream;

import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.fan.internal.FanConfiguration;
import io.aklivity.zilla.runtime.binding.fan.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.fan.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class FanServerFactory implements FanStreamFactory
{
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

    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final Long2ObjectHashMap<BindingConfig> bindings;

    private final MutableDirectBuffer writeBuffer;
    private final BindingHandler streamFactory;
    private final BudgetCreditor creditor;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;

    private final Long2ObjectHashMap<FanServerGroup> groupsByBindingId;

    public FanServerFactory(
        FanConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.creditor = context.creditor();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyTraceId = context::supplyTraceId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.bindings = new Long2ObjectHashMap<>();
        this.groupsByBindingId = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, binding);
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
        MessageConsumer replyTo)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final BindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long initialId = begin.streamId();
            long authorization = begin.authorization();
            final long replyId = supplyReplyId.applyAsLong(initialId);

            final RouteConfig route = binding.routes.stream()
                    .filter(r -> r.authorized.test(authorization))
                    .findFirst()
                    .orElse(null);

            if (route != null)
            {
                final FanServerGroup group = supplyFanServerGroup(routedId, route.id);

                newStream = new FanServer(
                        group,
                        originId,
                        routedId,
                        initialId,
                        replyId,
                        replyTo)::onMemberMessage;
            }
        }

        return newStream;
    }

    private FanServerGroup supplyFanServerGroup(
        long originId,
        long routedId)
    {
        return groupsByBindingId.computeIfAbsent(routedId, r -> new FanServerGroup(originId, routedId));
    }

    private final class FanServerGroup
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MessageConsumer receiver;
        private final List<FanServer> members;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;
        private long initialBud;
        private long initialBudIndex;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        FanServerGroup(
            long originId,
            long routedId)
        {
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.members = new CopyOnWriteArrayList<>();

            long traceId = supplyTraceId.getAsLong();
            this.receiver = newStream(this::onGroupMessage, originId, routedId,
                    initialId, initialSeq, initialAck, initialMax, traceId, 0L, 0L, e -> {});
            this.state = FanState.openingInitial(state);
        }

        private void onGroupMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onGroupBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onGroupData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onGroupEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onGroupAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onGroupFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onGroupReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onGroupWindow(window);
                break;
            default:
                // ignore
                break;
            }
        }

        private void onGroupBegin(
            BeginFW begin)
        {
            state = FanState.openingReply(state);

            final long traceId = begin.traceId();
            final long affinity = begin.affinity();
            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                member.doMemberBegin(traceId, affinity);
            }
        }

        private void onGroupData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                member.doMemberData(traceId, flags, budgetId, reserved, payload, extension);
            }
        }

        private void onGroupEnd(
            EndFW end)
        {
            state = FanState.closedReply(state);

            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                doEnd(member.receiver, member.originId, member.routedId, member.replyId,
                        member.replySeq, member.replyAck, member.replyMax);
            }

            doGroupEnd();
        }

        private void onGroupAbort(
            AbortFW abort)
        {
            state = FanState.closedReply(state);

            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                doAbort(member.receiver, member.originId, member.routedId, member.replyId,
                        member.replySeq, member.replyAck, member.replyMax);
            }

            doGroupAbort();
        }

        private void onGroupFlush(
            FlushFW flush)
        {
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                doFlush(member.receiver, member.originId, member.routedId, member.replyId,
                        member.replySeq, member.replyAck, member.replyMax,
                        budgetId, reserved);
            }
        }

        private void onGroupReset(
            ResetFW reset)
        {
            state = FanState.closedInitial(state);

            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                doReset(member.receiver, member.originId, member.routedId, member.initialId,
                        member.initialSeq, member.initialAck, member.initialMax);
            }

            doGroupReset();
        }

        private void onGroupWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            final long credit = Math.max(maximum - initialMax + acknowledge - initialAck, 0);
            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;

            assert initialAck <= initialSeq;

            if (!FanState.initialOpened(state))
            {
                long initialBudIndex = NO_CREDITOR_INDEX;
                long initialBud = budgetId;
                if (initialBud == NO_BUDGET_ID)
                {
                    initialBud = supplyBudgetId.getAsLong();
                    initialBudIndex = creditor.acquire(initialBud);
                }
                this.initialBud = initialBud;
                this.initialBudIndex = initialBudIndex;

                state = FanState.openedInitial(state);
            }

            if (initialBudIndex != NO_CREDITOR_INDEX && credit > 0)
            {
                creditor.credit(traceId, initialBudIndex, credit);
            }

            final int pendingAck = (int)(initialSeq - initialAck);
            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                member.doMemberWindow(traceId, initialBud, initialMax, pendingAck, initialPad);
            }
        }

        @Override
        public String toString()
        {
            return String.format("[%s] routedId=%016x", getClass().getSimpleName(), routedId);
        }

        private void doGroupData(
            long traceId,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            doData(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, flags, budgetId, reserved, payload, extension);

            initialSeq += reserved;
            assert initialSeq <= initialAck + initialMax;
        }

        private void doGroupEnd()
        {
            if (!FanState.initialClosed(state))
            {
                state = FanState.closedInitial(state);

                doEnd(receiver, originId, routedId, initialId, replySeq, replyAck, replyMax);

                cleanupInitial();
            }
        }

        private void doGroupAbort()
        {
            if (!FanState.initialClosed(state))
            {
                state = FanState.closedInitial(state);

                doAbort(receiver, originId, routedId, initialId, replySeq, replyAck, replyMax);

                cleanupInitial();
            }
        }

        private void doGroupFlush(
            long traceId,
            long budgetId,
            int reserved)
        {
            doFlush(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax, budgetId, reserved);
        }

        private void doGroupReset()
        {
            if (!FanState.replyClosed(state))
            {
                state = FanState.closedReply(state);

                doReset(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax);

                cleanupInitial();
            }
        }

        private void doGroupWindow(
            long traceId)
        {
            int windowMax = 0;
            int pendingAck = 0;
            int paddingMin = 0;

            // TODO: optimize, cache
            for (int i = 0; i < members.size(); i++)
            {
                final FanServer member = members.get(i);
                windowMax = Math.max(windowMax, member.replyMax);
                pendingAck = Math.max(pendingAck, (int)(member.replySeq - member.replyAck));
                paddingMin = Math.max(paddingMin, member.replyPad);
            }

            long replyAckMax = Math.max(replySeq - pendingAck, replyAck);
            if (replyAckMax > replyAck || windowMax > replyMax || !FanState.replyOpened(state))
            {
                replyAck = Math.max(replyAck, replyAckMax);
                replyMax = Math.max(replyMax, windowMax);
                assert replyAck <= replySeq;

                state = FanState.openedReply(state);

                doWindow(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, 0L, paddingMin);
            }
        }

        private void cleanupInitial()
        {
            if (initialBudIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(initialBudIndex);
                initialBudIndex = NO_CREDITOR_INDEX;
            }
        }

        private void join(
            FanServer member)
        {
            members.add(member);
        }

        private void leave(
            FanServer member)
        {
            members.remove(member);
        }
    }

    private final class FanServer
    {
        private final FanServerGroup group;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final MessageConsumer receiver;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private long replyBudIndex;
        private BudgetDebitor replyDeb;

        private int state;

        private FanServer(
            FanServerGroup group,
            long originId,
            long routedId,
            long initialId,
            long replyId,
            MessageConsumer receiver)
        {
            this.group = group;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.receiver = receiver;
        }

        private void onMemberMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMemberBegin(begin);
                group.join(this);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMemberData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMemberEnd(end);
                group.leave(this);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMemberAbort(abort);
                group.leave(this);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMemberFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMemberReset(reset);
                group.leave(this);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMemberWindow(window);
                break;
            default:
                break;
            }
        }

        private void onMemberBegin(
            BeginFW begin)
        {
            final long affinity = begin.affinity();

            state = FanState.openingInitial(state);

            doMemberBegin(supplyTraceId.getAsLong(), affinity);

            if (FanState.initialOpened(group.state))
            {
                doMemberWindow(supplyTraceId.getAsLong(), group.initialBud, group.initialMax,
                        (int)(group.initialSeq - group.initialAck), group.initialPad);
            }
        }

        private void onMemberData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int flags = data.flags();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            group.doGroupData(traceId, flags, budgetId, reserved, payload, extension);
        }

        private void onMemberEnd(
            EndFW end)
        {
            state = FanState.closedInitial(state);

            doMemberEnd();
        }

        private void onMemberAbort(
            AbortFW abort)
        {
            state = FanState.closedInitial(state);

            doMemberAbort();
        }

        private void onMemberFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            group.doGroupFlush(traceId, budgetId, reserved);
        }

        private void onMemberReset(
            ResetFW reset)
        {
            state = FanState.closedReply(state);

            cleanupReply();

            doMemberReset();
        }

        private void onMemberWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyPad = padding;
            this.replyBud = budgetId;

            assert replyAck <= replySeq;

            if (!FanState.replyOpened(state))
            {
                state = FanState.openedReply(state);

                if (replyBud != NO_BUDGET_ID)
                {
                    replyDeb = supplyDebitor.apply(replyBud);
                    replyBudIndex = replyDeb.acquire(replyBud, replyId,
                        tid -> replyDeb.claim(tid, replyBudIndex, replyId, 0, 0, 0));
                }
            }

            group.doGroupWindow(traceId);
        }

        private void doMemberReset()
        {
            if (!FanState.initialClosed(state))
            {
                state = FanState.closedInitial(state);

                doReset(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax);
            }
        }

        private void doMemberWindow(
            long traceId,
            long budgetId,
            int windowMax,
            int pendingAck,
            int paddingMin)
        {
            long initialAckMax = Math.max(initialSeq - pendingAck, initialAck);
            if (initialAckMax > initialAck || windowMax > initialMax || !FanState.initialOpened(state))
            {
                initialAck = initialAckMax;
                initialMax = windowMax;
                assert initialAck <= initialSeq;

                state = FanState.openedInitial(state);

                doWindow(receiver, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, budgetId, paddingMin);
            }
        }

        private void doMemberBegin(
            long traceId,
            long affinity)
        {
            if (FanState.replyOpening(group.state) && !FanState.replyOpening(state))
            {
                state = FanState.openingReply(state);

                doBegin(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, affinity);
            }
        }

        private void doMemberData(
            long traceId,
            int flags,
            long budgetId,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            if (replyBud != NO_BUDGET_ID)
            {
                reserved = replyDeb.claim(traceId, replyBudIndex, replyId, reserved, reserved, 0);
            }

            // TODO: cache + replay
            if (reserved != 0L)
            {
                doData(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax, traceId, flags,
                        replyBud, reserved, payload, extension);

                replySeq += reserved;
                assert replySeq <= replyAck + replyMax;
            }
        }

        private void doMemberEnd()
        {
            if (!FanState.replyClosed(state))
            {
                state = FanState.closedReply(state);

                doEnd(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax);
            }
        }

        private void doMemberAbort()
        {
            if (!FanState.replyClosed(state))
            {
                state = FanState.closedReply(state);

                doAbort(receiver, originId, routedId, replyId, replySeq, replyAck, replyMax);
            }
        }

        private void cleanupReply()
        {
            if (replyBud != NO_BUDGET_ID)
            {
                replyDeb.release(replyBudIndex, replyId);
                replyBudIndex = NO_DEBITOR_INDEX;
                replyDeb = null;
            }
        }
    }

    private MessageConsumer newStream(
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
        Consumer<OctetsFW.Builder> extension)
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

        MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
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
        long affinity)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(affinity)
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
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
                .extension(extension)
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(supplyTraceId.getAsLong())
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
        long budgetId,
        int reserved)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(supplyTraceId.getAsLong())
                .budgetId(budgetId)
                .reserved(reserved)
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(supplyTraceId.getAsLong())
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .padding(padding)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(supplyTraceId.getAsLong())
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }
}
