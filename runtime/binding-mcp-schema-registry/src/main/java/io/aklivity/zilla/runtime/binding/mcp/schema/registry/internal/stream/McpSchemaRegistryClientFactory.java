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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.McpSchemaRegistryConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.McpSchemaRegistryCompositeRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config.composite.McpSchemaRegistryCompositeGenerator;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpSchemaRegistryClientFactory implements BindingHandler
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

    private final Long2ObjectHashMap<McpSchemaRegistryBindingConfig> bindings;
    private final McpSchemaRegistryCompositeGenerator generator;

    public McpSchemaRegistryClientFactory(
        McpSchemaRegistryConfiguration config,
        EngineContext context)
    {
        this.context = context;
        this.writeBuffer = context.writeBuffer();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.generator = new McpSchemaRegistryCompositeGenerator();
    }

    public void attach(
        BindingConfig binding)
    {
        McpSchemaRegistryBindingConfig attached = new McpSchemaRegistryBindingConfig(context, binding);
        bindings.put(binding.id, attached);

        McpSchemaRegistryCompositeConfig composite = generator.generate(attached);
        assert composite != null;

        composite.namespaces.forEach(context::attachComposite);
        attached.composite = composite;
    }

    public void detach(
        long bindingId)
    {
        McpSchemaRegistryBindingConfig binding = bindings.remove(bindingId);
        McpSchemaRegistryCompositeConfig composite = binding != null ? binding.composite : null;

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

        final McpSchemaRegistryBindingConfig binding = bindings.get(routedId);
        final McpSchemaRegistryCompositeConfig composite = binding != null ? binding.composite : null;

        MessageConsumer newStream = null;

        if (binding != null && composite != null)
        {
            final McpSchemaRegistryCompositeRouteConfig route = composite.resolve();

            if (route != null)
            {
                newStream = new McpSchemaRegistryStream(
                    receiver,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    route.id)::onMcpSchemaRegistryMessage;
            }
        }

        return newStream;
    }

    private final class McpSchemaRegistryStream
    {
        private final McpOpenapiStream openapi;
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;

        private int state;

        private McpSchemaRegistryStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            long resolvedId)
        {
            this.openapi = new McpOpenapiStream(this, routedId, resolvedId, authorization);
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
        }

        private void onMcpSchemaRegistryMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpSchemaRegistryReset(reset);
                break;
            default:
                break;
            }
        }

        private void onMcpSchemaRegistryBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            state = McpSchemaRegistryState.openingInitial(state);

            openapi.doMcpOpenapiBegin(sequence, acknowledge, maximum, traceId, affinity, extension);
        }

        private void onMcpSchemaRegistryData(
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

            openapi.doMcpOpenapiData(sequence, acknowledge, maximum, traceId, authorization,
                budgetId, flags, reserved, payload, extension);
        }

        private void onMcpSchemaRegistryEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = McpSchemaRegistryState.closeInitial(state);

            openapi.doMcpOpenapiEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpSchemaRegistryFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            openapi.doMcpOpenapiFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onMcpSchemaRegistryAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = McpSchemaRegistryState.closeInitial(state);

            openapi.doMcpOpenapiAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpSchemaRegistryReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            state = McpSchemaRegistryState.closeReply(state);

            openapi.doMcpOpenapiReset(sequence, acknowledge, maximum, traceId);
        }

        private void onMcpSchemaRegistryWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            openapi.doMcpOpenapiWindow(sequence, acknowledge, maximum, traceId, authorization, budgetId, padding);
        }

        private void doMcpSchemaRegistryBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpSchemaRegistryState.replyOpening(state))
            {
                state = McpSchemaRegistryState.openingReply(state);

                McpSchemaRegistryClientFactory.this.doBegin(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, affinity, extension);
            }
        }

        private void doMcpSchemaRegistryData(
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
            McpSchemaRegistryClientFactory.this.doData(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, flags, reserved, payload, extension);
        }

        private void doMcpSchemaRegistryFlush(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            McpSchemaRegistryClientFactory.this.doFlush(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doMcpSchemaRegistryEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            doMcpSchemaRegistryBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!McpSchemaRegistryState.replyClosed(state))
            {
                McpSchemaRegistryClientFactory.this.doEnd(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = McpSchemaRegistryState.closeReply(state);
        }

        private void doMcpSchemaRegistryAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            doMcpSchemaRegistryBegin(sequence, acknowledge, maximum, traceId, extension);

            if (!McpSchemaRegistryState.replyClosed(state))
            {
                McpSchemaRegistryClientFactory.this.doAbort(sender, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);
            }

            state = McpSchemaRegistryState.closeReply(state);
        }

        private void doMcpSchemaRegistryReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId)
        {
            if (!McpSchemaRegistryState.initialClosed(state))
            {
                state = McpSchemaRegistryState.closeInitial(state);

                McpSchemaRegistryClientFactory.this.doReset(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization);
            }
        }

        private void doMcpSchemaRegistryWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long budgetId,
            int padding)
        {
            McpSchemaRegistryClientFactory.this.doWindow(sender, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
        }
    }

    private final class McpOpenapiStream
    {
        private final McpSchemaRegistryStream delegate;
        private final long originId;
        private final long routedId;
        private final long authorization;
        private final long initialId;
        private final long replyId;

        private MessageConsumer receiver;
        private int state;

        private McpOpenapiStream(
            McpSchemaRegistryStream delegate,
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMcpOpenapiFlush(flush);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpOpenapiEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpOpenapiAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpOpenapiReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpOpenapiWindow(window);
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

            state = McpSchemaRegistryState.openingReply(state);

            delegate.doMcpSchemaRegistryBegin(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpOpenapiData(
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

            delegate.doMcpSchemaRegistryData(sequence, acknowledge, maximum, traceId, budgetId,
                flags, reserved, payload, extension);
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

            delegate.doMcpSchemaRegistryFlush(sequence, acknowledge, maximum, traceId, budgetId, reserved, extension);
        }

        private void onMcpOpenapiEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();
            final OctetsFW extension = end.extension();

            state = McpSchemaRegistryState.closingReply(state);

            delegate.doMcpSchemaRegistryEnd(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpOpenapiAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();
            final OctetsFW extension = abort.extension();

            state = McpSchemaRegistryState.closingReply(state);

            delegate.doMcpSchemaRegistryAbort(sequence, acknowledge, maximum, traceId, extension);
        }

        private void onMcpOpenapiReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            state = McpSchemaRegistryState.closeInitial(state);

            delegate.doMcpSchemaRegistryReset(sequence, acknowledge, maximum, traceId);
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

            state = McpSchemaRegistryState.openingInitial(state);

            delegate.doMcpSchemaRegistryWindow(sequence, acknowledge, maximum, traceId, budgetId, padding);
        }

        private void doMcpOpenapiBegin(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long affinity,
            OctetsFW extension)
        {
            if (!McpSchemaRegistryState.initialOpening(state))
            {
                assert state == 0;

                this.receiver = McpSchemaRegistryClientFactory.this.newStream(this::onMcpOpenapiMessage, originId, routedId,
                    initialId, sequence, acknowledge, maximum, traceId, authorization, affinity, extension);
                state = McpSchemaRegistryState.openingInitial(state);
            }
        }

        private void doMcpOpenapiData(
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
            McpSchemaRegistryClientFactory.this.doData(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
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
            McpSchemaRegistryClientFactory.this.doFlush(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                traceId, authorization, budgetId, reserved, extension);
        }

        private void doMcpOpenapiEnd(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpSchemaRegistryState.initialClosed(state))
            {
                McpSchemaRegistryClientFactory.this.doEnd(receiver, originId, routedId, initialId, sequence, acknowledge, maximum,
                    traceId, authorization, extension);

                state = McpSchemaRegistryState.closeInitial(state);
            }
        }

        private void doMcpOpenapiAbort(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            OctetsFW extension)
        {
            if (!McpSchemaRegistryState.initialClosed(state))
            {
                McpSchemaRegistryClientFactory.this.doAbort(receiver, originId, routedId, initialId, sequence, acknowledge,
                    maximum, traceId, authorization, extension);

                state = McpSchemaRegistryState.closeInitial(state);
            }
        }

        private void doMcpOpenapiReset(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId)
        {
            if (!McpSchemaRegistryState.replyClosed(state))
            {
                McpSchemaRegistryClientFactory.this.doReset(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
                    traceId, authorization);

                state = McpSchemaRegistryState.closeReply(state);
            }
        }

        private void doMcpOpenapiWindow(
            long sequence,
            long acknowledge,
            int maximum,
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            McpSchemaRegistryClientFactory.this.doWindow(receiver, originId, routedId, replyId, sequence, acknowledge, maximum,
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
