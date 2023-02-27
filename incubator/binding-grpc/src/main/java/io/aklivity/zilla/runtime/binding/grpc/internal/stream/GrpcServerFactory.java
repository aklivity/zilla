/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.stream;

import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;

import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;


import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcBinding;
import io.aklivity.zilla.runtime.binding.grpc.internal.GrpcConfiguration;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcBindingConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcMethodConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.config.GrpcRouteConfig;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcKind;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;


public final class GrpcServerFactory implements GrpcStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(new byte[0]), 0, 0);
    private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
    private static final String8FW HEADER_NAME_STATUS = new String8FW(":status");
    private static final String16FW HEADER_VALUE_METHOD_POST = new String16FW("POST");
    private static final String16FW HEADER_VALUE_STATUS_405 = new String16FW("405");
    private static final String16FW HEADER_VALUE_STATUS_200 = new String16FW("200");
    private static final String8FW HEADER_NAME_CONTENT_LENGTH = new String8FW("content-length");
    private static final String16FW HEADER_VALUE_CONTENT_LENGTH_0 = new String16FW("0");
    private static final String8FW HEADER_NAME_GRPC_STATUS = new String8FW("grpc-status");
    private static final String16FW HEADER_VALUE_GRPC_UNIMPLEMENTED = new String16FW("12");
    private static final String16FW HEADER_VALUE_GRPC_INTERNAL_ERROR = new String16FW("13");

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final GrpcBeginExFW grpcBeginExRO = new GrpcBeginExFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final GrpcBeginExFW.Builder grpcBeginExRW = new GrpcBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final BufferPool bufferPool;
    private final BudgetCreditor creditor;
    private final Signaler signaler;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final LongFunction<BudgetDebitor> supplyDebitor;
    private final Long2ObjectHashMap<GrpcBindingConfig> bindings;
    private final int grpcTypeId;
    private final int httpTypeId;


    public GrpcServerFactory(
        GrpcConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.bufferPool = context.bufferPool();
        this.creditor = context.creditor();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.grpcTypeId = context.supplyTypeId(GrpcBinding.NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        GrpcBindingConfig grpcBinding = new GrpcBindingConfig(binding);
        bindings.put(binding.id, grpcBinding);
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
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();
        final OctetsFW extension = begin.extension();
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        MessageConsumer newStream = null;

        if (!isGrpcRequestMethod(httpBeginEx))
        {
            doHttpResponse(begin, network, HEADER_VALUE_STATUS_405, HEADER_VALUE_GRPC_INTERNAL_ERROR);
            newStream = (t, b, i, l) -> {};
        }
        else
        {
            final GrpcBindingConfig binding = bindings.get(routeId);

            final GrpcMethodConfig methodConfig = binding.resolveGrpcMethodConfig(httpBeginEx);

            if (methodConfig != null)
            {
                newStream = newInitialGrpcStream(begin, network, methodConfig);
            }
            else
            {
                doHttpResponse(begin, network, HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED);
                newStream = (t, b, i, l) -> {};
            }
        }

        return newStream;
    }

    public MessageConsumer newInitialGrpcStream(
        final BeginFW begin,
        final MessageConsumer network,
        final GrpcMethodConfig methodConfig)
    {
        final long routeId = begin.routeId();
        final long initialId = begin.streamId();
        final long replyId = supplyReplyId.applyAsLong(initialId);
        final long affinity = begin.affinity();

        GrpcBindingConfig binding = bindings.get(routeId);

        GrpcRouteConfig route = null;

        if (binding != null)
        {
            route = binding.resolve(begin.authorization(), methodConfig);
        }

        MessageConsumer newStream = null;

        if (route != null)
        {

            newStream = new GrpcServer(
                network,
                routeId,
                initialId,
                replyId,
                affinity,
                methodConfig,
                0)::onNetMessage;
        }
        else
        {
            doHttpResponse(begin, network, HEADER_VALUE_STATUS_200, HEADER_VALUE_GRPC_UNIMPLEMENTED);

            newStream = (t, b, i, l) -> {};
        }

        return newStream;
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
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
        long budgetId,
        int reserved,
        DirectBuffer buffer,
        int index,
        int length,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                .routeId(routeId)
                                .streamId(streamId)
                                .sequence(sequence)
                                .acknowledge(acknowledge)
                                .maximum(maximum)
                                .traceId(traceId)
                                .authorization(authorization)
                                .budgetId(budgetId)
                                .reserved(reserved)
                                .payload(buffer, index, length)
                                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        long budgetId,
        int reserved,
        int flags,
        DirectBuffer buffer,
        int index,
        int length,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
                                .payload(buffer, index, length)
                                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
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
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                              .routeId(routeId)
                              .streamId(streamId)
                              .sequence(sequence)
                              .acknowledge(acknowledge)
                              .maximum(maximum)
                              .traceId(traceId)
                              .authorization(authorization)
                              .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                                  .routeId(routeId)
                                  .streamId(streamId)
                                  .sequence(sequence)
                                  .acknowledge(acknowledge)
                                  .maximum(maximum)
                                  .traceId(traceId)
                                  .authorization(authorization)
                                  .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                  .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
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
                                    .capabilities(capabilities)
                                    .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity()).routeId(routeId)
                                     .streamId(streamId)
                                     .sequence(sequence)
                                     .acknowledge(acknowledge)
                                     .maximum(maximum)
                                     .traceId(traceId)
                                     .authorization(authorization)
                                     .extension(extension.buffer(), extension.offset(), extension.sizeof())
                                     .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
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
        int reserved,
        Consumer<OctetsFW.Builder> extension)
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
                                     .extension(extension)
                                     .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }


    private final class GrpcServer
    {
        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final GrpcStream stream;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int state;

        private GrpcMethodConfig methodConfig;

        private GrpcServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long replyId,
            long affinity,
            GrpcMethodConfig methodConfig,
            long budgetId)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = replyId;
            this.affinity = affinity;
            this.methodConfig = methodConfig;

            this.stream = new GrpcStream(routeId);
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
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetSignal(signal);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            initialSeq = sequence;
            initialAck = acknowledge;

            state = GrpcState.openingInitial(state);

            stream.doAppBegin(traceId, authorization, affinity);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;

            assert initialAck <= initialSeq;

            stream.doAppData(traceId, authorization, budgetId, reserved, flags, payload);
        }

        private void onNetEnd(
            EndFW end)
        {
            final long authorization = end.authorization();
            final long traceId = end.traceId();
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
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
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
        }

        private void onNetSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            Flyweight flyweight)
        {
            doNetData(traceId, authorization, budgetId, flyweight.buffer(), flyweight.offset(), flyweight.limit());
        }

        private void doNetData(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
        }

        private void doNetEndIfNecessary(
            long traceId,
            long authorization)
        {
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
        }

        private void doNetAbortIfNecessary(
            long traceId,
            long authorization)
        {
        }

        private void doNetAbort(
            long traceId,
            long authorization)
        {
        }

        private void doNetResetIfNecessary(
            long traceId,
            long authorization)
        {
        }

        private void doNetReset(
            long traceId,
            long authorization)
        {
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            int padding,
            long budgetId,
            int minInitialNoAck,
            int minInitialMax)
        {
        }

        private void onGrpcWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {

        }

        private void doHttpWindow(
            long authorization,
            long traceId,
            long budgetId,
            int padding,
            int capabilities)
        {
            initialAck = stream.initialAck;
            initialMax = stream.initialMax;

            doWindow(network, routeId, initialId, initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding, capabilities);
        }

        private final class GrpcStream
        {
            private MessageConsumer application;
            private final long routeId;
            private final long initialId;
            private final long replyId;

            private int state;

            private long initialSeq;
            private long initialAck;
            private int initialMax;
            private int initialPad;
            private long initialBud;

            private BudgetDebitor initialDebitor;
            private long initialDebitorIndex = NO_DEBITOR_INDEX;

            private long replySeq;
            private long replyAck;
            private int replyMax;
            private int messageContentLength;

            private GrpcStream(
                long routeId)
            {
                this.routeId = routeId;
                this.initialId = supplyInitialId.applyAsLong(routeId);
                this.replyId = supplyReplyId.applyAsLong(this.initialId);
            }

            private void doAppBegin(
                long traceId,
                long authorization,
                long affinity)
            {
                application = newGrpcStream(this::onAppMessage, routeId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization, affinity, methodConfig.method, methodConfig.request, methodConfig.response);
            }


            private void doAppData(
                long traceId,
                long authorization,
                long budgetId,
                int reserved,
                int flags,
                OctetsFW payload)
            {
                assert GrpcState.initialOpening(state);
                if (messageContentLength == 0)
                {

                }

            }

            private void doAppEnd(
                long traceId,
                Flyweight extension)
            {
                if (!GrpcState.initialOpened(state))
                {
                    state = GrpcState.closingInitial(state);
                }
            }

            private void doAppAbort(
                long traceId,
                Flyweight extension)
            {
                doAbort(application, routeId, initialId, initialSeq, initialAck, initialMax, traceId, 0, extension);
            }

            private void doAppAbortIfNecessary(
                long traceId)
            {
                if (!GrpcState.initialClosed(state))
                {
                    doAppAbort(traceId, EMPTY_OCTETS);
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
                case ResetFW.TYPE_ID:
                    final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                    onAppReset(reset);
                    break;
                case WindowFW.TYPE_ID:
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    onAppWindow(window);
                    break;
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
                }
            }

            private void onAppReset(
                ResetFW reset)
            {

                final long traceId = reset.traceId();

                cleanup(traceId);
            }

            private void onAppWindow(
                WindowFW window)
            {
                final long sequence = window.sequence();
                final long acknowledge = window.acknowledge();
                final long traceId = window.traceId();
                final long budgetId = window.budgetId();
                final long authorization = window.authorization();
                final int maximum = window.maximum();
                final int padding = window.padding();
                final int capabilities = window.capabilities();

                assert acknowledge <= sequence;
                assert sequence <= initialSeq;
                assert acknowledge >= initialAck;
                assert maximum >= initialMax;

                state = GrpcState.openInitial(state);

                initialAck = acknowledge;
                initialMax = maximum;
                initialPad = padding;
                initialBud = budgetId;

                doHttpWindow(authorization, traceId, budgetId, padding, capabilities);
            }

            private void onAppBegin(
                BeginFW begin)
            {
                final long sequence = begin.sequence();
                final long acknowledge = begin.acknowledge();
                final long traceId = begin.traceId();
                final long authorization = begin.authorization();

                state = GrpcState.openReply(state);

                assert acknowledge <= sequence;
                assert sequence >= replySeq;
                assert acknowledge >= replyAck;

                replySeq = sequence;
                replyAck = acknowledge;
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
                assert acknowledge <= replyAck;


            }

            private void onAppEnd(
                EndFW end)
            {

            }

            private void onAppAbort(
                AbortFW abort)
            {
            }


            private void doAppReset(
                long traceId)
            {
                doReset(application, routeId, replyId, replySeq, replyAck, replyMax, traceId, 0, EMPTY_OCTETS);
            }

            private void doAppResetIfNecessary(
                long traceId)
            {
                if (!GrpcState.replyClosed(state))
                {
                    doAppReset(traceId);
                }
            }

            private void cleanup(
                long traceId)
            {
                doAppAbortIfNecessary(traceId);
                doAppResetIfNecessary(traceId);
            }
        }
    }

    private void doHttpBegin(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW>> mutator)
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

    private void doHttpResponse(
        BeginFW begin,
        MessageConsumer acceptReply,
        String16FW httpStatus,
        String16FW grpcStatus)
    {
        final long sequence = begin.sequence();
        final long acknowledge = begin.acknowledge();
        final long acceptRouteId = begin.routeId();
        final long acceptInitialId = begin.streamId();
        final long acceptReplyId = supplyReplyId.applyAsLong(acceptInitialId);
        final long affinity = begin.affinity();
        final long traceId = begin.traceId();

        doWindow(acceptReply, acceptRouteId, acceptInitialId, sequence, acknowledge, 0, traceId, 0L, 0, 0, 0);
        doHttpBegin(acceptReply, acceptRouteId, acceptReplyId, 0L, 0L, 0, traceId, 0L, affinity, hs ->
            hs.item(h -> h.name(HEADER_NAME_STATUS).value(httpStatus))
                .item(h -> h.name(HEADER_NAME_CONTENT_LENGTH).value(HEADER_VALUE_CONTENT_LENGTH_0))
                .item(h -> h.name(HEADER_NAME_GRPC_STATUS).value(grpcStatus)));
        doHttpEnd(acceptReply, acceptRouteId, acceptReplyId, 0L, 0L, 0, traceId, 0L);
    }

    private void doHttpEnd(
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

    private MessageConsumer newGrpcStream(
        MessageConsumer sender,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        String method,
        GrpcKind request,
        GrpcKind response)
    {
        final GrpcBeginExFW grpcBegin = grpcBeginExRW.wrap(writeBuffer, BeginFW.FIELD_OFFSET_EXTENSION, writeBuffer.capacity())
            .typeId(grpcTypeId)
            .method(new String16FW(method))
            .request(r -> r.set(request).build())
            .response(r -> r.set(response).build())
            .build();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(grpcBegin.buffer(), grpcBegin.offset(), grpcBegin.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private static boolean isGrpcRequestMethod(
        HttpBeginExFW httpBeginEx)
    {
        return httpBeginEx != null &&
            httpBeginEx.headers().anyMatch(h -> HEADER_NAME_METHOD.equals(h.name()) &&
                HEADER_VALUE_METHOD_POST.equals(h.value()));
    }
}
