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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiBinding;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.McpOpenApiConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenApiBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.McpOpenApiRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.OpenapiBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.openapi.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpOpenApiProxyFactory implements McpOpenApiStreamFactory
{
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

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

    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final OpenapiBeginExFW.Builder openapiBeginExRW = new OpenapiBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;

    private final Long2ObjectHashMap<McpOpenApiBindingConfig> bindings;
    private final int mcpTypeId;
    private final int openapiTypeId;

    public McpOpenApiProxyFactory(
        McpOpenApiConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.mcpTypeId = context.supplyTypeId("mcp");
        this.openapiTypeId = context.supplyTypeId("openapi");
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return openapiTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        McpOpenApiBindingConfig newBinding = new McpOpenApiBindingConfig(binding);
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
        final OctetsFW extension = begin.extension();

        final McpOpenApiBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final McpBeginExFW mcpBeginEx = extension.get(mcpBeginExRO::tryWrap);
            final String kind = mcpBeginEx != null ? mcpBeginEx.kind().asString() : null;

            final McpOpenApiRouteConfig route = binding.resolve(authorization, kind, kind);

            if (route != null)
            {
                final long resolvedId = route.id;
                final String apiId = route.with != null ? route.with.apiId : null;
                final String operationId = route.with != null ? route.with.operationId : null;

                final McpProxy proxy = new McpProxy(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    resolvedId,
                    apiId,
                    operationId);

                proxy.onMcpBegin(begin);
                newStream = proxy::onMcpMessage;
            }
        }

        return newStream;
    }

    private void doMcpBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long affinity)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
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
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
            .traceId(traceId)
            .authorization(authorization)
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(0)
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
        long traceId,
        long authorization,
        long budgetId,
        int credit,
        int padding)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(0)
            .acknowledge(0)
            .maximum(credit)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private final class McpProxy
    {
        private final MessageConsumer mcpSender;
        private final long originId;
        private final long routedId;
        private final long mcpInitialId;
        private final long mcpReplyId;
        private final long affinity;
        private final long authorization;
        private final long resolvedId;
        private final String apiId;
        private final String operationId;

        private long openapiInitialId;
        private long openapiReplyId;
        private MessageConsumer openapiReceiver;

        private int mcpState;
        private int openapiState;

        private long mcpInitialSeq;
        private long mcpInitialAck;
        private int mcpInitialMax;

        private long mcpReplySeq;
        private long mcpReplyAck;
        private int mcpReplyMax;
        private int mcpReplyPad;

        private long openapiInitialSeq;
        private long openapiInitialAck;
        private int openapiInitialMax;

        private long openapiReplySeq;
        private long openapiReplyAck;
        private int openapiReplyMax;
        private int openapiReplyPad;

        private McpProxy(
            MessageConsumer mcpSender,
            long originId,
            long routedId,
            long mcpInitialId,
            long affinity,
            long authorization,
            long resolvedId,
            String apiId,
            String operationId)
        {
            this.mcpSender = mcpSender;
            this.originId = originId;
            this.routedId = routedId;
            this.mcpInitialId = mcpInitialId;
            this.mcpReplyId = supplyReplyId.applyAsLong(mcpInitialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.resolvedId = resolvedId;
            this.apiId = apiId;
            this.operationId = operationId;
        }

        private void onMcpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMcpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMcpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMcpEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMcpFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMcpAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMcpWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMcpReset(reset);
                break;
            default:
                break;
            }
        }

        private void onMcpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            mcpInitialSeq = begin.sequence();
            mcpInitialAck = begin.acknowledge();
            mcpInitialMax = begin.maximum();
            mcpState = McpOpenApiProxyState.openingInitial(mcpState);

            openapiInitialId = supplyInitialId.applyAsLong(resolvedId);
            openapiReplyId = supplyReplyId.applyAsLong(openapiInitialId);

            final long resolvedApiId = apiId != null ? apiId.hashCode() : 0L;

            final OpenapiBeginExFW openapiBeginEx = openapiBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(openapiTypeId)
                .apiId(resolvedApiId)
                .operationId(operationId)
                .extension(EMPTY_OCTETS)
                .build();

            final BeginFW openapiBegin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(resolvedId)
                .streamId(openapiInitialId)
                .sequence(0)
                .acknowledge(0)
                .maximum(0)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(openapiBeginEx.buffer(), openapiBeginEx.offset(), openapiBeginEx.sizeof())
                .build();

            openapiReceiver = streamFactory.newStream(openapiBegin.typeId(),
                openapiBegin.buffer(), openapiBegin.offset(), openapiBegin.sizeof(),
                this::onOpenApiMessage);

            if (openapiReceiver != null)
            {
                openapiReceiver.accept(openapiBegin.typeId(),
                    openapiBegin.buffer(), openapiBegin.offset(), openapiBegin.sizeof());

                openapiState = McpOpenApiProxyState.openingInitial(openapiState);
            }
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            mcpInitialSeq = data.sequence() + data.reserved();

            if (openapiReceiver != null && payload != null)
            {
                doData(openapiReceiver, originId, resolvedId, openapiInitialId,
                    traceId, authorization, budgetId, flags, reserved,
                    payload, 0, payload.capacity());
            }
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            mcpState = McpOpenApiProxyState.closedInitial(mcpState);

            if (openapiReceiver != null)
            {
                doEnd(openapiReceiver, originId, resolvedId, openapiInitialId, traceId, authorization);
                openapiState = McpOpenApiProxyState.closedInitial(openapiState);
            }
        }

        private void onMcpFlush(
            FlushFW flush)
        {
            // pass-through
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            mcpState = McpOpenApiProxyState.closedInitial(mcpState);

            if (openapiReceiver != null)
            {
                doAbort(openapiReceiver, originId, resolvedId, openapiInitialId, traceId, authorization);
                openapiState = McpOpenApiProxyState.closedInitial(openapiState);
            }
        }

        private void onMcpWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            mcpReplySeq = window.sequence();
            mcpReplyAck = window.acknowledge();
            mcpReplyMax = window.maximum();
            mcpReplyPad = window.padding();
            mcpState = McpOpenApiProxyState.openedReply(mcpState);

            if (openapiReceiver != null)
            {
                doWindow(openapiReceiver, originId, resolvedId, openapiReplyId,
                    traceId, authorization, budgetId, credit, padding);
                openapiState = McpOpenApiProxyState.openedReply(openapiState);
            }
        }

        private void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            mcpState = McpOpenApiProxyState.closedReply(mcpState);

            if (openapiReceiver != null)
            {
                doReset(openapiReceiver, originId, resolvedId, openapiReplyId, traceId, authorization);
                openapiState = McpOpenApiProxyState.closedReply(openapiState);
            }
        }

        private void onOpenApiMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onOpenApiBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onOpenApiData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onOpenApiEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onOpenApiFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onOpenApiAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onOpenApiWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onOpenApiReset(reset);
                break;
            default:
                break;
            }
        }

        private void onOpenApiBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            openapiReplySeq = begin.sequence();
            openapiReplyAck = begin.acknowledge();
            openapiState = McpOpenApiProxyState.openedReply(openapiState);

            doMcpBegin(mcpSender, originId, routedId, mcpReplyId, traceId, authorization, affinity);
            mcpState = McpOpenApiProxyState.openingReply(mcpState);
        }

        private void onOpenApiData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            openapiReplySeq = data.sequence() + data.reserved();

            if (payload != null)
            {
                doData(mcpSender, originId, routedId, mcpReplyId,
                    traceId, authorization, budgetId, flags, reserved,
                    payload, 0, payload.capacity());
            }
        }

        private void onOpenApiEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            openapiState = McpOpenApiProxyState.closedReply(openapiState);

            doEnd(mcpSender, originId, routedId, mcpReplyId, traceId, authorization);
            mcpState = McpOpenApiProxyState.closedReply(mcpState);
        }

        private void onOpenApiFlush(
            FlushFW flush)
        {
            // pass-through
        }

        private void onOpenApiAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            openapiState = McpOpenApiProxyState.closedReply(openapiState);

            doAbort(mcpSender, originId, routedId, mcpReplyId, traceId, authorization);
            mcpState = McpOpenApiProxyState.closedReply(mcpState);
        }

        private void onOpenApiWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            openapiInitialSeq = window.sequence();
            openapiInitialAck = window.acknowledge();
            openapiInitialMax = window.maximum();
            openapiState = McpOpenApiProxyState.openedInitial(openapiState);

            doWindow(mcpSender, originId, routedId, mcpInitialId,
                traceId, authorization, budgetId, credit, padding);
            mcpState = McpOpenApiProxyState.openedInitial(mcpState);
        }

        private void onOpenApiReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            openapiState = McpOpenApiProxyState.closedInitial(openapiState);

            doReset(mcpSender, originId, routedId, mcpInitialId, traceId, authorization);
            mcpState = McpOpenApiProxyState.closedInitial(mcpState);
        }
    }
}
