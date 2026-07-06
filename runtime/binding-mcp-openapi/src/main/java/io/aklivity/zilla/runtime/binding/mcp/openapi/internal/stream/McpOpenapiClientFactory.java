/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenapiConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenapiCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.composite.McpOpenapiCompositeGenerator;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.event.McpOpenapiEventContext;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpOpenapiClientFactory implements BindingHandler
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBufferEx(new byte[0]), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final FlushFW flushRO = new FlushFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();

    private final EngineContext context;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final MutableDirectBufferEx writeBuffer;

    private final Long2ObjectHashMap<McpOpenapiBindingConfig> bindings;
    private final McpOpenapiCompositeGenerator generator;
    private final McpOpenapiEventContext event;
    private final long compositeRouteId;

    public McpOpenapiClientFactory(
        McpOpenapiConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.generator = new McpOpenapiCompositeGenerator(config.httpClientExit());
        this.event = new McpOpenapiEventContext(context);
        this.compositeRouteId = config.compositeRouteId();
    }

    public void attach(
        BindingConfig binding)
    {
        McpOpenapiBindingConfig attached = new McpOpenapiBindingConfig(context, binding);
        bindings.put(binding.id, attached);

        if (attached.routes.isEmpty())
        {
            event.routesEmpty(binding.id);
        }

        McpOpenapiCompositeConfig composite = generator.generate(attached);
        for (String reason : generator.deniedOperations())
        {
            event.operationDenied(binding.id, reason);
        }
        assert composite != null;

        composite.namespaces.forEach(context::attachComposite);
        attached.composite = composite;
    }

    public void detach(
        long bindingId)
    {
        McpOpenapiBindingConfig binding = bindings.remove(bindingId);
        McpOpenapiCompositeConfig composite = binding != null ? binding.composite : null;

        if (composite != null)
        {
            composite.namespaces.forEach(context::detachComposite);
        }
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer receiver)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();

        final McpOpenapiBindingConfig binding = bindings.get(routedId);
        final McpOpenapiCompositeConfig composite = binding != null ? binding.composite : null;

        MessageConsumer newStream = null;

        if (binding != null && composite != null)
        {
            final McpOpenapiCompositeRouteConfig route = composite.resolve();

            if (route != null)
            {
                final long resolvedId = compositeRouteId != -1L ? compositeRouteId : route.id;

                newStream = new McpOpenapiStream(
                    receiver,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    resolvedId)::onMcpOpenapiMessage;
            }
        }

        return newStream;
    }

    private final class McpOpenapiStream
    {
        private final McpHttpStream http;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private McpOpenapiStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            long resolvedId)
        {
            this.http = new McpHttpStream(this, routedId, resolvedId, authorization);
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onMcpOpenapiMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMcpOpenapiBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMcpOpenapiData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpOpenapiEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMcpOpenapiFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpOpenapiAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpOpenapiWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpOpenapiReset(reset);
                break;
            default:
                break;
            }
        }

        private void onMcpOpenapiBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpOpenapiState.openingInitial(state);

            http.doMcpHttpBegin(sequence, acknowledge, maximum, traceId, affinity, extension);
        }

        private void onMcpOpenapiData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();
            final int flags = data.flags();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            http.doMcpHttpData(sequence, acknowledge, maximum, traceId, authorization,
                budgetId, flags, reserved, payload, extension);
        }

        private void onMcpOpenapiEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = McpOpenapiState.closeInitial(state);

            http.doMcpHttpEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpOpenapiFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            http.doMcpHttpFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onMcpOpenapiAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = McpOpenapiState.closeInitial(state);

            http.doMcpHttpAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpOpenapiReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            state = McpOpenapiState.closeReply(state);

            http.doMcpHttpReset(sequence, acknowledge, maximum, traceId);
        }

        private void onMcpOpenapiWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            http.doMcpHttpWindow(sequence, acknowledge, maximum, traceId, authorization, budgetId, padding);
        }

        private void doMcpOpenapiBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpOpenapiState.replyOpening(state))
            {
                state = McpOpenapiState.openingReply(state);

                McpOpenapiClientFactory.this.doBegin(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, affinity, extension);
            }
        }

        private void doMcpOpenapiData(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            McpOpenapiClientFactory.this.doData(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, flags, reserved, payload, extension);
        }

        private void doMcpOpenapiFlush(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            McpOpenapiClientFactory.this.doFlush(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doMcpOpenapiEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            // an END must always be deliverable even if the reply was never opened (e.g. mcp_http0 ends its
            // reply before ever sending a BEGIN) — open the reply first, mirroring doMcpOpenapiAbort below
            doMcpOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!McpOpenapiState.replyClosed(state))
            {
                McpOpenapiClientFactory.this.doEnd(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = McpOpenapiState.closeReply(state);
        }

        private void doMcpOpenapiAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            // an ABORT must always be deliverable even if the reply was never opened — e.g. mcp_http0's
            // upstream aborts before ever sending a response, so no reply BEGIN has gone out yet. Without
            // opening the reply first, this abort is silently dropped and the real client hangs forever
            // waiting for any reply frame at all
            doMcpOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!McpOpenapiState.replyClosed(state))
            {
                McpOpenapiClientFactory.this.doAbort(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = McpOpenapiState.closeReply(state);
        }

        private void doMcpOpenapiReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId)
        {
            if (!McpOpenapiState.initialClosed(state))
            {
                state = McpOpenapiState.closeInitial(state);

                McpOpenapiClientFactory.this.doReset(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization);
            }
        }

        private void doMcpOpenapiWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int padding)
        {
            McpOpenapiClientFactory.this.doWindow(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
        }
    }

    private final class McpHttpStream
    {
        private final McpOpenapiStream delegate;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private McpHttpStream(
            McpOpenapiStream delegate,
            long originId,
            long routedId,
            long authorization)
        {
            this.delegate = delegate;
            this.originId = originId;
            this.routedId = routedId;
            this.receiver = MessageConsumer.NOOP;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.authorization = authorization;
        }

        private void onMcpHttpMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMcpHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMcpHttpData(data);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMcpHttpFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpHttpAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpHttpReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpHttpWindow(window);
                break;
            default:
                break;
            }
        }

        private void onMcpHttpBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpOpenapiState.openingReply(state);

            delegate.doMcpOpenapiBegin(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpHttpData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final int maximum = data.maximum();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final OctetsFW payload = data.payload();
            final OctetsFW extension = data.extension();

            delegate.doMcpOpenapiData(sequence, acknowledge, maximum, traceId, budgetId,
                flags, reserved, payload, extension);
        }

        private void onMcpHttpFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            delegate.doMcpOpenapiFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onMcpHttpEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = McpOpenapiState.closingReply(state);

            delegate.doMcpOpenapiEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpHttpAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = McpOpenapiState.closingReply(state);

            delegate.doMcpOpenapiAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpHttpReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            state = McpOpenapiState.closeInitial(state);

            delegate.doMcpOpenapiReset(sequence, acknowledge, maximum, traceId);
        }

        private void onMcpHttpWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            state = McpOpenapiState.openingInitial(state);

            delegate.doMcpOpenapiWindow(sequence, acknowledge, maximum, traceId, budgetId, padding);
        }

        private void doMcpHttpBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long affinity,
            OctetsFW extension)
        {
            if (!McpOpenapiState.initialOpening(state))
            {
                assert state == 0;

                this.receiver = McpOpenapiClientFactory.this.newStream(this::onMcpHttpMessage, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization, affinity, extension);
                state = McpOpenapiState.openingInitial(state);
            }
        }

        private void doMcpHttpData(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            long budgetId,
            int flags,
            int reserved,
            OctetsFW payload,
            OctetsFW extension)
        {
            McpOpenapiClientFactory.this.doData(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, flags, reserved, payload, extension);
        }

        private void doMcpHttpFlush(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            McpOpenapiClientFactory.this.doFlush(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doMcpHttpEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpOpenapiState.initialClosed(state))
            {
                McpOpenapiClientFactory.this.doEnd(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = McpOpenapiState.closeInitial(state);
            }
        }

        private void doMcpHttpAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpOpenapiState.initialClosed(state))
            {
                McpOpenapiClientFactory.this.doAbort(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = McpOpenapiState.closeInitial(state);
            }
        }

        private void doMcpHttpReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId)
        {
            if (!McpOpenapiState.replyClosed(state))
            {
                McpOpenapiClientFactory.this.doReset(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization);

                state = McpOpenapiState.closeReply(state);
            }
        }

        private void doMcpHttpWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            McpOpenapiClientFactory.this.doWindow(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
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
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        final MessageConsumer receiver =
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
        long authorization,
        long affinity,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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
        long budgetId,
        int flags,
        int reserved,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW frame = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(frame.typeId(), frame.buffer(), frame.offset(), frame.sizeof());
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

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
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
            .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
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
            .extension(EMPTY_OCTETS.buffer(), EMPTY_OCTETS.offset(), EMPTY_OCTETS.sizeof())
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
