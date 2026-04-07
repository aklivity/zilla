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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.stream;

import java.util.Map;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.config.McpHttpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.http.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpHttpProxyFactory implements BindingHandler
{
    private static final String MCP_TYPE_NAME = "mcp";
    private static final String HTTP_TYPE_NAME = "http";

    private final OctetsFW emptyRO = new OctetsFW().wrap(new UnsafeBuffer(0L, 0), 0, 0);

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int mcpTypeId;
    private final int httpTypeId;

    private final Long2ObjectHashMap<McpHttpBindingConfig> bindings;

    public McpHttpProxyFactory(
        McpHttpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public int routedTypeId()
    {
        return httpTypeId;
    }

    public void attach(
        BindingConfig binding)
    {
        McpHttpBindingConfig newBinding = new McpHttpBindingConfig(binding);
        bindings.put(binding.id, newBinding);
    }

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
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        final McpHttpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            String tool = null;

            final McpBeginExFW mcpBeginEx = mcpBeginExRO.tryWrap(begin.extension().buffer(),
                begin.extension().offset(), begin.extension().limit());

            if (mcpBeginEx != null)
            {
                tool = mcpBeginEx.kind().asString();
            }

            final McpHttpRouteConfig route = binding.resolve(authorization, tool);

            if (route != null)
            {
                final McpProxy mcpProxy = new McpProxy(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    route.id,
                    affinity,
                    authorization,
                    route);
                newStream = mcpProxy::onMcpMessage;
            }
        }

        return newStream;
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
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
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
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

    private HttpBeginExFW buildHttpBeginEx(
        McpHttpRouteConfig route)
    {
        final String method = route.resolveMethod();
        final Map<String, String> headers = route.resolveHeaders(null);

        final HttpBeginExFW.Builder builder = httpBeginExRW.wrap(extBuffer, 0, extBuffer.capacity())
            .typeId(httpTypeId);

        builder.headersItem(h -> h.name(":method").value(method != null ? method : "GET"));

        if (headers != null)
        {
            for (Map.Entry<String, String> entry : headers.entrySet())
            {
                final String name = entry.getKey();
                final String value = entry.getValue();
                builder.headersItem(h -> h.name(name).value(value));
            }
        }

        return builder.build();
    }

    private final class McpProxy
    {
        private final MessageConsumer mcp;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final McpHttpRouteConfig route;

        private HttpProxy http;
        private int state;

        private McpProxy(
            MessageConsumer mcp,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpHttpRouteConfig route)
        {
            this.mcp = mcp;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.route = route;
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

            state = McpHttpState.openingInitial(state);

            final HttpBeginExFW httpBeginEx = buildHttpBeginEx(route);

            http = new HttpProxy(this, originId, resolvedId, affinity, authorization);
            http.doHttpBegin(traceId, httpBeginEx);

            doMcpWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onMcpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            if (http != null && payload != null)
            {
                http.doHttpData(traceId, budgetId, flags, reserved, payload, 0, payload.capacity());
            }
        }

        private void onMcpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpHttpState.closedInitial(state);

            if (http != null)
            {
                http.doHttpEnd(traceId);
            }
        }

        private void onMcpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpHttpState.closedInitial(state);

            if (http != null)
            {
                http.doHttpAbort(traceId);
            }
        }

        private void onMcpWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            if (http != null)
            {
                http.doHttpWindow(traceId, budgetId, credit, padding);
            }
        }

        private void onMcpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpHttpState.closedReply(state);

            if (http != null)
            {
                http.doHttpReset(traceId);
            }
        }

        private void doMcpBegin(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                state = McpHttpState.openedReply(state);
                doBegin(mcp, originId, routedId, replyId, traceId, authorization, affinity, emptyRO);
            }
        }

        private void doMcpData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            doData(mcp, originId, routedId, replyId, traceId, authorization,
                budgetId, flags, reserved, payload, offset, length);
        }

        private void doMcpEnd(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                state = McpHttpState.closedReply(state);
                doEnd(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }

        private void doMcpAbort(
            long traceId)
        {
            if (!McpHttpState.replyClosed(state))
            {
                state = McpHttpState.closedReply(state);
                doAbort(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }

        private void doMcpWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            doWindow(mcp, originId, routedId, replyId, traceId, authorization, budgetId, credit, padding);
        }

        private void doMcpReset(
            long traceId)
        {
            if (!McpHttpState.initialClosed(state))
            {
                state = McpHttpState.closedInitial(state);
                doReset(mcp, originId, routedId, replyId, traceId, authorization);
            }
        }
    }

    private final class HttpProxy
    {
        private final McpProxy peer;
        private final long originId;
        private final long resolvedId;
        private final long affinity;
        private final long authorization;
        private final long httpInitialId;
        private final long httpReplyId;
        private final MessageConsumer http;

        private int state;

        private HttpProxy(
            McpProxy peer,
            long originId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            this.peer = peer;
            this.originId = originId;
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.httpInitialId = supplyInitialId.applyAsLong(resolvedId);
            this.httpReplyId = supplyReplyId.applyAsLong(httpInitialId);
            this.http = streamFactory.newStream(BeginFW.TYPE_ID, writeBuffer, 0, 0, this::onHttpMessage);
        }

        private void onHttpMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onHttpBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onHttpData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onHttpEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onHttpAbort(abort);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onHttpWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onHttpReset(reset);
                break;
            default:
                break;
            }
        }

        private void onHttpBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = McpHttpState.openedReply(state);

            peer.doMcpBegin(traceId);
            doHttpWindow(traceId, 0, writeBuffer.capacity(), 0);
        }

        private void onHttpData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int flags = data.flags();
            final int reserved = data.reserved();
            final DirectBuffer payload = data.payload();

            if (payload != null)
            {
                peer.doMcpData(traceId, budgetId, flags, reserved, payload, 0, payload.capacity());
            }
        }

        private void onHttpEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = McpHttpState.closedReply(state);

            peer.doMcpEnd(traceId);
        }

        private void onHttpAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = McpHttpState.closedReply(state);

            peer.doMcpAbort(traceId);
        }

        private void onHttpWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int credit = window.maximum();
            final int padding = window.padding();

            peer.doMcpWindow(traceId, budgetId, credit, padding);
        }

        private void onHttpReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = McpHttpState.closedInitial(state);

            peer.doMcpReset(traceId);
        }

        private void doHttpBegin(
            long traceId,
            HttpBeginExFW extension)
        {
            if (http != null)
            {
                state = McpHttpState.openingInitial(state);
                doBegin(http, originId, resolvedId, httpInitialId, traceId, authorization, affinity, extension);
            }
        }

        private void doHttpData(
            long traceId,
            long budgetId,
            int flags,
            int reserved,
            DirectBuffer payload,
            int offset,
            int length)
        {
            if (http != null)
            {
                doData(http, originId, resolvedId, httpInitialId, traceId, authorization,
                    budgetId, flags, reserved, payload, offset, length);
            }
        }

        private void doHttpEnd(
            long traceId)
        {
            if (http != null && !McpHttpState.initialClosed(state))
            {
                state = McpHttpState.closedInitial(state);
                doEnd(http, originId, resolvedId, httpInitialId, traceId, authorization);
            }
        }

        private void doHttpAbort(
            long traceId)
        {
            if (http != null && !McpHttpState.initialClosed(state))
            {
                state = McpHttpState.closedInitial(state);
                doAbort(http, originId, resolvedId, httpInitialId, traceId, authorization);
            }
        }

        private void doHttpWindow(
            long traceId,
            long budgetId,
            int credit,
            int padding)
        {
            if (http != null)
            {
                doWindow(http, originId, resolvedId, httpReplyId, traceId, authorization, budgetId, credit, padding);
            }
        }

        private void doHttpReset(
            long traceId)
        {
            if (http != null && !McpHttpState.replyClosed(state))
            {
                state = McpHttpState.closedReply(state);
                doReset(http, originId, resolvedId, httpReplyId, traceId, authorization);
            }
        }
    }
}
