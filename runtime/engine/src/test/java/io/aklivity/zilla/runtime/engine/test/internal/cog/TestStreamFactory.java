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
package io.aklivity.zilla.runtime.engine.test.internal.cog;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.types.stream.WindowFW;

final class TestStreamFactory implements StreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();

    private final DataFW dataRO = new DataFW();
    private final DataFW.Builder dataRW = new DataFW.Builder();

    private final EndFW endRO = new EndFW();
    private final EndFW.Builder endRW = new EndFW.Builder();

    private final AbortFW abortRO = new AbortFW();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();

    private final FlushFW flushRO = new FlushFW();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final ResetFW resetRO = new ResetFW();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final WindowFW windowRO = new WindowFW();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final ChallengeFW challengeRO = new ChallengeFW();
    private final ChallengeFW.Builder challengeRW = new ChallengeFW.Builder();

    private final EngineContext context;
    private final Long2LongHashMap router;

    TestStreamFactory(
        EngineContext context)
    {
        this.context = context;
        this.router = new Long2LongHashMap(-1L);
    }

    public void attach(
        BindingConfig binding)
    {
        if (binding.exit != null)
        {
            router.put(binding.id, binding.exit.id);
        }
    }

    public void detach(
        BindingConfig binding)
    {
        router.remove(binding.id);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer source)
    {
        BeginFW begin = beginRO.wrap(buffer, index, index + length);
        long routeId = begin.routeId();
        long initialId = begin.streamId();
        long replyId = initialId ^ 1L;
        long resolvedId = router.get(routeId);

        MessageConsumer newStream =  null;

        if (resolvedId != -1L)
        {
            newStream = new TestSource(source, routeId, initialId, replyId, resolvedId)::onMessage;
        }

        return newStream;
    }

    private final class TestSource
    {
        private final MessageConsumer source;
        private final long routeId;
        private final long initialId;
        private final long replyId;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private long initialBud;
        private int initialPad;
        private int initialCap;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBud;
        private int replyCap;

        private final TestTarget target;

        private TestSource(
            MessageConsumer source,
            long routeId,
            long initialId,
            long replyId,
            long resolvedId)
        {
            this.source = source;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.target = new TestTarget(resolvedId);
        }

        private void onMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                DataFW data = dataRO.wrap(buffer, index, index + length);
                onInitialData(data);
                break;
            case EndFW.TYPE_ID:
                EndFW end = endRO.wrap(buffer, index, index + length);
                onInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onInitialAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onInitialFlush(flush);
                break;
            case ResetFW.TYPE_ID:
                ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onReplyReset(reset);
                break;
            case WindowFW.TYPE_ID:
                WindowFW window = windowRO.wrap(buffer, index, index + length);
                onReplyWindow(window);
                break;
            case ChallengeFW.TYPE_ID:
                ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                onReplyChallenge(challenge);
                break;
            }
        }

        private void onInitialBegin(
            BeginFW begin)
        {
            long traceId = begin.traceId();

            target.doInitialBegin(traceId);
        }

        private void onInitialData(
            DataFW data)
        {
            long sequence = data.sequence();
            long traceId = data.traceId();
            int reserved = data.reserved();
            int flags = data.flags();
            OctetsFW payload = data.payload();

            initialSeq = sequence + reserved;

            target.doInitialData(traceId, flags, reserved, payload);
        }

        private void onInitialEnd(
            EndFW end)
        {
            long traceId = end.traceId();

            target.doInitialEnd(traceId);
        }

        private void onInitialAbort(
            AbortFW abort)
        {
            long traceId = abort.traceId();

            target.doInitialAbort(traceId);
        }

        private void onInitialFlush(
            FlushFW flush)
        {
            long traceId = flush.traceId();
            int reserved = flush.reserved();

            target.doInitialFlush(traceId, reserved);
        }

        private void onReplyReset(
            ResetFW reset)
        {
            long traceId = reset.traceId();

            target.doReplyReset(traceId);
        }

        private void onReplyWindow(
            WindowFW window)
        {
            long traceId = window.traceId();

            replyAck = window.acknowledge();
            replyMax = window.maximum();
            replyPad = window.padding();
            replyBud = window.budgetId();
            replyCap = window.capabilities();

            target.doReplyWindow(traceId, replyAck, replyMax, replyBud, replyPad, replyCap);
        }

        private void onReplyChallenge(
            ChallengeFW challenge)
        {
            long traceId = challenge.traceId();

            target.doReplyChallenge(traceId);
        }

        private void doInitialReset(
            long traceId)
        {
            doReset(source, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
        }

        private void doInitialWindow(
            long traceId,
            long acknowledge,
            int maximum,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = acknowledge;
            initialMax = maximum;
            initialBud = budgetId;
            initialPad = padding;
            initialCap = capabilities;

            doWindow(source, routeId, initialId, initialSeq, initialAck, initialMax, traceId,
                    initialBud, initialPad, initialCap);
        }

        private void doInitialChallenge(
            long traceId)
        {
            doChallenge(source, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
        }

        private void doReplyBegin(
            long traceId)
        {
            doBegin(source, routeId, replyId, replySeq, replyAck, replyMax, traceId);
        }

        private void doReplyData(
            long traceId,
            int flags,
            int reserved,
            OctetsFW payload)
        {
            doData(source, routeId, replyId, replySeq, replyAck, replyMax, replyBud, traceId, flags, reserved, payload);

            replySeq += reserved;
        }

        private void doReplyEnd(
            long traceId)
        {
            doEnd(source, routeId, replyId, replySeq, replyAck, replyPad, traceId);
        }

        private void doReplyAbort(
            long traceId)
        {
            doAbort(source, routeId, replyId, replySeq, replyAck, replyPad, traceId);
        }

        private void doReplyFlush(
            long traceId,
            int reserved)
        {
            doFlush(source, routeId, replyId, replySeq, replyAck, replyPad, traceId, replyBud, reserved);
        }

        private final class TestTarget
        {
            private MessageConsumer target;
            private final long routeId;
            private final long initialId;
            private final long replyId;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private long initialBud;
            private int initialPad;
            private int initialCap;

            private long replySeq;
            private long replyAck;
            private int replyMax;
            private int replyPad;
            private long replyBud;
            private int replyCap;

            private final TestSource source;

            private TestTarget(
                long routeId)
            {
                this.routeId = routeId;
                this.initialId = context.supplyInitialId(routeId);
                this.replyId = context.supplyReplyId(initialId);
                this.source = TestSource.this;
            }

            private void onMessage(
                int msgTypeId,
                DirectBuffer buffer,
                int index,
                int length)
            {
                switch (msgTypeId)
                {
                case ResetFW.TYPE_ID:
                    ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onInitialReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onInitialWindow(window);
                    break;
                case ChallengeFW.TYPE_ID:
                    ChallengeFW challenge = challengeRO.wrap(buffer, index, index + length);
                    onInitialChallenge(challenge);
                    break;
                case BeginFW.TYPE_ID:
                    BeginFW begin = beginRO.wrap(buffer, index, index + length);
                    onReplyBegin(begin);
                    break;
                case DataFW.TYPE_ID:
                    DataFW data = dataRO.wrap(buffer, index, index + length);
                    onReplyData(data);
                    break;
                case EndFW.TYPE_ID:
                    EndFW end = endRO.wrap(buffer, index, index + length);
                    onReplyEnd(end);
                    break;
                case AbortFW.TYPE_ID:
                    AbortFW abort = abortRO.wrap(buffer, index, index + length);
                    onReplyAbort(abort);
                    break;
                case FlushFW.TYPE_ID:
                    FlushFW flush = flushRO.wrap(buffer, index, index + length);
                    onReplyFlush(flush);
                    break;
                }
            }

            private void onInitialReset(
                ResetFW reset)
            {
                long traceId = reset.traceId();

                source.doInitialReset(traceId);
            }

            private void onInitialWindow(
                WindowFW window)
            {
                long traceId = window.traceId();

                initialMax = window.maximum();
                initialAck = window.acknowledge();
                initialPad = window.padding();
                initialBud = window.budgetId();
                initialCap = window.capabilities();

                source.doInitialWindow(traceId, initialAck, initialMax, initialBud, initialPad, initialCap);
            }

            private void onInitialChallenge(
                ChallengeFW challenge)
            {
                long traceId = challenge.traceId();

                source.doInitialChallenge(traceId);
            }

            private void onReplyBegin(
                BeginFW begin)
            {
                long traceId = begin.traceId();

                source.doReplyBegin(traceId);
            }

            private void onReplyData(
                DataFW data)
            {
                long sequence = data.sequence();
                long traceId = data.traceId();
                int reserved = data.reserved();
                int flags = data.flags();
                OctetsFW payload = data.payload();

                replySeq = sequence + reserved;

                source.doReplyData(traceId, flags, reserved, payload);
            }

            private void onReplyEnd(
                EndFW end)
            {
                long traceId = end.traceId();

                source.doReplyEnd(traceId);
            }

            private void onReplyAbort(
                AbortFW abort)
            {
                long traceId = abort.traceId();

                source.doReplyAbort(traceId);
            }

            private void onReplyFlush(
                FlushFW flush)
            {
                long traceId = flush.traceId();
                int reserved = flush.reserved();

                source.doReplyFlush(traceId, reserved);
            }

            private void doInitialBegin(
                long traceId)
            {
                target = newStream(this::onMessage, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialData(
                long traceId,
                int flags,
                int reserved,
                OctetsFW payload)
            {
                doData(target, routeId, initialId, initialSeq, initialAck, initialMax, initialBud,
                        traceId, flags, reserved, payload);

                initialSeq += reserved;
            }

            private void doInitialEnd(
                long traceId)
            {
                doEnd(target, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialAbort(
                long traceId)
            {
                doAbort(target, routeId, initialId, initialSeq, initialAck, initialMax, traceId);
            }

            private void doInitialFlush(
                long traceId,
                int reserved)
            {
                doFlush(target, routeId, initialId, initialSeq, initialAck, initialMax, traceId, initialBud, reserved);
            }

            private void doReplyReset(
                long traceId)
            {
                doReset(target, routeId, replyId, replySeq, replyAck, replyMax, traceId);
            }

            private void doReplyWindow(
                long traceId,
                long acknowledge,
                int maximum,
                long budgetId,
                int padding,
                int capabilities)
            {
                replyAck = acknowledge;
                replyMax = maximum;
                replyBud = budgetId;
                replyPad = padding;
                replyCap = capabilities;

                doWindow(target, routeId, replyId, replySeq, replyAck, replyMax, traceId, replyBud, replyPad, replyCap);
            }

            private void doReplyChallenge(
                long traceId)
            {
                doChallenge(target, routeId, replyId, replySeq, replyAck, replyMax, traceId);
            }
        }
    }

    private MessageConsumer newStream(
        MessageConsumer source,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();
        StreamFactory streamFactory = context.streamFactory();

        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(0L)
                .build();

        MessageConsumer stream =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), source);

        stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return stream;
    }

    private void doBegin(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .affinity(0L)
                .build();

        stream.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long budgetId,
        long traceId,
        int flags,
        int reserved,
        OctetsFW payload)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
                .build();

        stream.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int reserved)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .reserved(reserved)
                .build();

        stream.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doReset(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long budgetId,
        int padding,
        int capabilities)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .budgetId(budgetId)
                .padding(padding)
                .capabilities(capabilities)
                .build();

        stream.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doChallenge(
        MessageConsumer stream,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId)
    {
        MutableDirectBuffer writeBuffer = context.writeBuffer();

        ChallengeFW challenge = challengeRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .build();

        stream.accept(challenge.typeId(), challenge.buffer(), challenge.offset(), challenge.sizeof());
    }
}
