/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpServerFactory implements McpStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String KIND_SERVER = "server";

    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;

    public McpServerFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return httpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        McpBindingConfig newBinding = new McpBindingConfig(binding);
        bindings.put(binding.id, newBinding);
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
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long affinity = begin.affinity();
        final long authorization = begin.authorization();

        final McpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final long resolvedId = binding.resolveRoute(authorization, KIND_SERVER);

            if (resolvedId != -1L)
            {
                newStream = new McpServerStream(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    resolvedId,
                    affinity,
                    authorization)::onMessage;
            }
        }

        return newStream;
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
        DirectBuffer extBuf,
        int extOffset,
        int extLength)
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
            .extension(extBuf, extOffset, extLength)
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
        DirectBuffer payload,
        int offset,
        int length)
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
            .budgetId(budgetId)
            .reserved(reserved)
            .flags(flags)
            .payload(payload, offset, length)
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
        long authorization)
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
        long authorization)
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
        int reserved)
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
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
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

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private final class McpServerStream
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;

        private long downstreamInitialId;
        private long downstreamReplyId;
        private MessageConsumer downstream;

        private McpServerStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
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
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onReset(reset);
                break;
            default:
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();

            downstreamInitialId = supplyInitialId.applyAsLong(resolvedId);
            downstreamReplyId = supplyReplyId.applyAsLong(downstreamInitialId);

            final McpBeginExFW mcpBeginEx = mcpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(mcpTypeId)
                .kind(KIND_SERVER)
                .sessionId(String.valueOf(traceId))
                .build();

            final BeginFW downstreamBegin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(resolvedId)
                .streamId(downstreamInitialId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(mcpBeginEx.buffer(), mcpBeginEx.offset(), mcpBeginEx.sizeof())
                .build();

            downstream = streamFactory.newStream(
                downstreamBegin.typeId(),
                downstreamBegin.buffer(),
                downstreamBegin.offset(),
                downstreamBegin.sizeof(),
                this::onDownstreamMessage);

            if (downstream != null)
            {
                downstream.accept(
                    downstreamBegin.typeId(),
                    downstreamBegin.buffer(),
                    downstreamBegin.offset(),
                    downstreamBegin.sizeof());

                doWindow(sender, originId, routedId, initialId,
                    sequence, acknowledge, writeBuffer.capacity(),
                    traceId, authorization, 0, 0);
            }
            else
            {
                doReset(sender, originId, routedId, initialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onData(
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

            if (downstream != null && payload != null)
            {
                doData(downstream, originId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, flags, reserved,
                    payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();

            if (downstream != null)
            {
                doEnd(downstream, originId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();

            if (downstream != null)
            {
                doAbort(downstream, originId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization);
            }
        }

        private void onFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            if (downstream != null)
            {
                doFlush(downstream, originId, resolvedId, downstreamInitialId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, reserved);
            }
        }

        private void onWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            if (downstream != null)
            {
                doWindow(downstream, originId, resolvedId, downstreamReplyId,
                    sequence, acknowledge, maximum,
                    traceId, authorization, budgetId, padding);
            }
        }

        private void onReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            doReset(sender, originId, routedId, initialId,
                sequence, acknowledge, maximum, traceId, authorization);
        }

        private void onDownstreamMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDownstreamBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onDownstreamData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDownstreamEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDownstreamAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onDownstreamFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDownstreamWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDownstreamReset(reset);
                break;
            default:
                break;
            }
        }

        private void onDownstreamBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final int maximum = begin.maximum();
            final long traceId = begin.traceId();
            final OctetsFW extension = begin.extension();

            doBegin(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization, affinity,
                extension.buffer(), extension.offset(), extension.sizeof());

            doWindow(downstream, originId, resolvedId, downstreamReplyId,
                sequence, acknowledge, writeBuffer.capacity(),
                traceId, authorization, 0, 0);
        }

        private void onDownstreamData(
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

            if (payload != null)
            {
                doData(sender, originId, routedId, replyId,
                    sequence, acknowledge, maximum, traceId, authorization,
                    budgetId, flags, reserved,
                    payload.buffer(), payload.offset(), payload.sizeof());
            }
        }

        private void onDownstreamEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();
            final int maximum = end.maximum();
            final long traceId = end.traceId();

            doEnd(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }

        private void onDownstreamAbort(
            AbortFW abort)
        {
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();
            final int maximum = abort.maximum();
            final long traceId = abort.traceId();

            doAbort(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }

        private void onDownstreamFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final int maximum = flush.maximum();
            final long traceId = flush.traceId();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();

            doFlush(sender, originId, routedId, replyId,
                sequence, acknowledge, maximum, traceId, authorization,
                budgetId, reserved);
        }

        private void onDownstreamWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            doWindow(sender, originId, routedId, initialId,
                sequence, acknowledge, maximum,
                traceId, authorization, budgetId, padding);
        }

        private void onDownstreamReset(
            ResetFW reset)
        {
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();
            final int maximum = reset.maximum();
            final long traceId = reset.traceId();

            doReset(downstream, originId, resolvedId, downstreamReplyId,
                sequence, acknowledge, maximum, traceId, authorization);
        }
    }
}
