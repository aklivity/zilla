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
package io.aklivity.zilla.runtime.cog.kafka.internal.stream;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaCog;
import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;

public class KafkaCacheServerAddressFactory
{
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();

    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();

    private final int kafkaTypeId;
    private final LongSupplier supplyTraceId;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBuffer writeBuffer;
    private final StreamFactory streamFactory;
    private final LongFunction<KafkaBindingConfig> supplyBinding;

    private final Long2ObjectHashMap<List<KafkaAddressStream>> streams;

    public KafkaCacheServerAddressFactory(
        KafkaConfiguration config,
        AxleContext context,
        LongFunction<KafkaBindingConfig> supplyBinding)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaCog.NAME);
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.supplyTraceId = context::supplyTraceId;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBinding = supplyBinding;
        this.streams = new Long2ObjectHashMap<>();
    }

    void onAttached(
        long bindingId)
    {
        final KafkaBindingConfig binding = supplyBinding.apply(bindingId);
        final List<String> bootstrap = binding.options != null ? binding.options.bootstrap : null;

        if (bootstrap != null)
        {
            List<KafkaAddressStream> bootstraps = bootstrap.stream()
                .map(t -> new KafkaAddressStream(bindingId, 0, t))
                .collect(toList());

            streams.put(bindingId, bootstraps);

            bootstraps.forEach(KafkaAddressStream::doKafkaInitialBegin);
        }
    }

    void onDetached(
        long bindingId)
    {
        List<KafkaAddressStream> bootstraps = streams.remove(bindingId);

        if (bootstraps != null)
        {
            bootstraps.forEach(KafkaAddressStream::doKafkaInitialEnd);
        }
    }

    private final class KafkaAddressStream
    {
        private MessageConsumer receiver;
        private final long routeId;
        private final long authorization;
        private final long initialId;
        private final long replyId;
        private final String topic;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaAddressStream(
            long routeId,
            long authorization,
            String topic)
        {
            this.routeId = routeId;
            this.authorization = authorization;
            this.initialId = supplyInitialId.applyAsLong(routeId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.topic = topic;
        }

        private void doKafkaInitialBegin()
        {
            final long traceId = supplyTraceId.getAsLong();

            state = KafkaState.openingInitial(state);

            receiver = newStream(this::onKafkaReply, routeId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, 0,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .bootstrap(bs -> bs.topic(topic))
                        .build()
                        .sizeof()));
        }

        private void doKafkaInitialEnd()
        {
            final long traceId = supplyTraceId.getAsLong();

            state = KafkaState.closedInitial(state);

            doEnd(receiver, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void onKafkaReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final long traceId = supplyTraceId.getAsLong();

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                state = KafkaState.openedReply(state);
                doKafkaReplyWindow(traceId, 0);
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
                assert KafkaState.initialClosed(state) : "reply closed unexpectedly";
                state = KafkaState.closedReply(state);
                break;
            case ResetFW.TYPE_ID:
                assert KafkaState.replyClosing(state) : "initial closed unexpectedly";
                state = KafkaState.closedInitial(state);
                doKafkaReplyReset(traceId);
                break;
            case WindowFW.TYPE_ID:
                state = KafkaState.openedInitial(state);
                break;
            }
        }

        private void doKafkaReplyReset(
            long traceId)
        {
            doReset(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization);
        }

        private void doKafkaReplyWindow(
            long traceId,
            int padding)
        {
            doWindow(receiver, routeId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, 0L, padding);
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
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .routeId(routeId)
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

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                               .routeId(routeId)
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

    private void doReset(
        MessageConsumer sender,
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

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding)
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
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }
}
