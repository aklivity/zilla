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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import java.util.Map;
import java.util.function.LongUnaryOperator;

import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.common.json.DirectBufferInputStreamEx;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpClientFactory implements McpStreamFactory
{
    private static final String HTTP_TYPE_NAME = "http";
    private static final String MCP_TYPE_NAME = "mcp";

    private static final String HTTP_HEADER_METHOD = ":method";
    private static final String HTTP_HEADER_PATH = ":path";
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_MCP_VERSION = "mcp-protocol-version";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    private static final String MCP_PATH = "/mcp";
    private static final String HTTP_METHOD_POST = "POST";

    private static final int DATA_FLAGS_COMPLETE = 0x03;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final HttpBeginExFW.Builder httpBeginExRW = new HttpBeginExFW.Builder();
    private final McpBeginExFW.Builder mcpBeginExRW = new McpBeginExFW.Builder();

    private final DirectBufferInputStreamEx inputRO = new DirectBufferInputStreamEx();

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final MutableDirectBuffer codecBuffer;
    private final BufferPool bufferPool;
    private final BindingHandler streamFactory;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final int httpTypeId;
    private final int mcpTypeId;
    private final int decodeMax;
    private final String clientName;
    private final String clientVersion;

    private int requestId = 2;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpStream> sessions = new Object2ObjectHashMap<>();

    public McpClientFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.codecBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.bufferPool = context.bufferPool();
        this.streamFactory = context.streamFactory();
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.bindings = new Long2ObjectHashMap<>();
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
        this.decodeMax = bufferPool.slotCapacity();
        this.clientName = config.clientName();
        this.clientVersion = config.clientVersion();
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
        final OctetsFW extension = begin.extension();

        final McpBindingConfig binding = bindings.get(routedId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            final McpRouteConfig route = binding.resolve(authorization);

            if (route != null && extension.sizeof() > 0)
            {
                final McpBeginExFW mcpBeginEx = mcpBeginExRO.wrap(
                    extension.buffer(), extension.offset(), extension.limit());

                switch (mcpBeginEx.kind())
                {
                case McpBeginExFW.KIND_LIFECYCLE:
                    newStream = new McpLifecycleStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.lifecycle().sessionId().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_TOOLS_LIST:
                    newStream = new McpToolsListStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.toolsList().sessionId().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_TOOLS_CALL:
                    newStream = new McpToolsCallStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.toolsCall().sessionId().asString(),
                        mcpBeginEx.toolsCall().name().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_PROMPTS_LIST:
                    newStream = new McpPromptsListStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.promptsList().sessionId().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_PROMPTS_GET:
                    newStream = new McpPromptsGetStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.promptsGet().sessionId().asString(),
                        mcpBeginEx.promptsGet().name().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_RESOURCES_LIST:
                    newStream = new McpResourcesListStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.resourcesList().sessionId().asString())::onAppMessage;
                    break;
                case McpBeginExFW.KIND_RESOURCES_READ:
                    newStream = new McpResourcesReadStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.resourcesRead().sessionId().asString(),
                        mcpBeginEx.resourcesRead().uri().asString())::onAppMessage;
                    break;
                default:
                    break;
                }
            }
        }

        return newStream;
    }

    private abstract class McpStream
    {
        protected final MessageConsumer sender;
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long resolvedId;
        protected final long affinity;
        protected final long authorization;
        protected final String sessionId;

        protected HttpStream http;
        boolean appClosedEmpty;
        protected int appDataSlot = NO_SLOT;
        protected int appDataOffset;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private long replyBud;
        private int replyPad;

        private int state;

        McpStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.resolvedId = resolvedId;
            this.affinity = affinity;
            this.authorization = authorization;
            this.sessionId = sessionId;
        }

        void onAppMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
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
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onAppFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onAppWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onAppReset(reset);
                break;
            default:
                break;
            }
        }

        private void onAppBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = McpState.openingInitial(state);
            state = McpState.openedInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = writeBuffer.capacity();

            doAppWindow(traceId, authorization, 0L, 0);

            onAppBeginImpl(traceId);
        }

        abstract void onAppBeginImpl(long traceId);

        abstract void onAppEndImpl(long traceId);

        private void onAppData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();
            initialAck = initialSeq;

            assert initialAck <= initialSeq;

            final OctetsFW payload = data.payload();
            if (payload != null && payload.sizeof() > 0)
            {
                if (appDataSlot == NO_SLOT)
                {
                    appDataSlot = bufferPool.acquire(initialId);
                }
                if (appDataSlot == NO_SLOT)
                {
                    http.doNetAbort(data.traceId(), authorization);
                    doAppAbort(data.traceId());
                }
                else
                {
                    final MutableDirectBuffer slot = bufferPool.buffer(appDataSlot);
                    slot.putBytes(appDataOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    appDataOffset += payload.sizeof();
                }
            }

            doAppWindow(data.traceId(), authorization, 0L, 0);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            initialSeq = end.sequence();
            state = McpState.closedInitial(state);
            onAppEndImpl(traceId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            http.doNetAbort(abort.traceId(), authorization);
        }

        private void onAppFlush(
            FlushFW flush)
        {
        }

        private void onAppWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = window.budgetId();
            replyPad = window.padding();

            assert replyAck <= replySeq;
        }

        private void onAppReset(
            ResetFW reset)
        {
            http.doNetReset(reset.traceId(), authorization);
        }

        void doAppBegin(
            long traceId,
            McpBeginExFW beginEx)
        {
            state = McpState.openingReply(state);
            state = McpState.openedReply(state);

            doBegin(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, affinity,
                beginEx.buffer(), beginEx.offset(), beginEx.sizeof());
        }

        void doAppData(
            long traceId,
            DirectBuffer payload,
            int offset,
            int length)
        {
            final int reserved = length + replyPad;

            doData(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization,
                DATA_FLAGS_COMPLETE, replyBud, reserved,
                payload, offset, length);

            replySeq += reserved;
        }

        void doAppEnd(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doEnd(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        void doAppAbort(
            long traceId)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doAbort(sender, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);
            }
        }

        void doAppReset(
            long traceId)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doReset(sender, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        private void doAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            doWindow(sender, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, budgetId, padding);
        }
    }

    private final class McpLifecycleStream extends McpStream
    {
        McpLifecycleStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            sessions.put(sessionId, this);
            this.http = new HttpInitializeRequest(this);
        }

        @Override
        void onAppBeginImpl(
            long traceId)
        {
            http.doNetBegin(traceId, authorization);
        }

        @Override
        void onAppEndImpl(
            long traceId)
        {
            sessions.remove(sessionId);

            new HttpTerminateSession(this).doNetBegin(traceId, authorization);
        }
    }

    private abstract class McpRequestStream extends McpStream
    {
        int assignedRequestId;

        McpRequestStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.assignedRequestId = requestId++;
        }

        @Override
        final void onAppBeginImpl(
            long traceId)
        {
            http.doNetBegin(traceId, authorization);
        }

        @Override
        final void onAppEndImpl(
            long traceId)
        {
            if (appDataSlot != NO_SLOT)
            {
                final DirectBuffer slot = bufferPool.buffer(appDataSlot);
                http.doNetBodyAndEnd(traceId, authorization, slot, 0, appDataOffset);
                bufferPool.release(appDataSlot);
                appDataSlot = NO_SLOT;
                appDataOffset = 0;
            }
            else
            {
                appClosedEmpty = true;
            }
        }
    }

    private final class McpToolsListStream extends McpRequestStream
    {
        McpToolsListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpToolsListStream(this);
        }
    }

    private final class McpToolsCallStream extends McpRequestStream
    {
        McpToolsCallStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId,
            String toolName)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpToolsCallStream(this, toolName);
        }
    }

    private final class McpPromptsListStream extends McpRequestStream
    {
        McpPromptsListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpPromptsListStream(this);
        }
    }

    private final class McpPromptsGetStream extends McpRequestStream
    {
        McpPromptsGetStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId,
            String promptName)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpPromptsGetStream(this, promptName);
        }
    }

    private final class McpResourcesListStream extends McpRequestStream
    {
        McpResourcesListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpResourcesListStream(this);
        }
    }

    private final class McpResourcesReadStream extends McpRequestStream
    {
        McpResourcesReadStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId,
            String resourceUri)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId);
            this.http = new HttpResourcesReadStream(this, resourceUri);
        }
    }

    private abstract class HttpStream
    {
        protected final long originId;
        protected final long routedId;
        protected final long initialId;
        protected final long replyId;
        protected final long affinity;
        protected final McpStream mcp;

        protected MessageConsumer net;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;
        private long encodeSlotAuthorization;

        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;

        private int state;

        HttpStream(
            McpStream mcp)
        {
            this.originId = mcp.originId;
            this.routedId = mcp.resolvedId;
            this.initialId = supplyInitialId.applyAsLong(routedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = mcp.affinity;
            this.mcp = mcp;
        }

        void onNetMessage(
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
                onNetEndImpl(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetReset(reset);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.openedReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;

            assert replyAck <= replySeq;

            doNetWindow(begin.traceId(), begin.authorization(), 0L, 0);
            onNetBeginImpl(begin);
        }

        private void onNetData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;

            onNetDataImpl(data);
            doNetWindow(data.traceId(), data.authorization(), 0L, 0);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + flush.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;
            doNetWindow(flush.traceId(), flush.authorization(), 0L, 0);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            if (net != null)
            {
                doWindow(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization, budgetId, padding);
            }
        }

        private void onNetAbort(
            AbortFW abort)
        {
            state = McpState.closedReply(state);
            cleanupEncodeSlot();
            cleanupResponseSlot();
            if (!mcp.appClosedEmpty)
            {
                doNotifyCancelled(abort.traceId());
            }
            mcp.doAppAbort(abort.traceId());
        }

        protected void doNotifyCancelled(
            long traceId)
        {
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            state = McpState.openedInitial(state);
            initialMax = maximum;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                final long traceId = encodeSlotTraceId;
                final long authorization = encodeSlotAuthorization;
                cleanupEncodeSlot();

                encodeNet(traceId, authorization, encodeBuffer, 0, limit);
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            state = McpState.closedInitial(state);
            cleanupEncodeSlot();
            cleanupResponseSlot();
            mcp.doAppReset(reset.traceId());
        }

        abstract void onNetBeginImpl(BeginFW begin);

        abstract void onNetDataImpl(DataFW data);

        abstract void onNetEndImpl(EndFW end);

        abstract void doNetBegin(long traceId, long authorization);

        void doNetBodyAndEnd(
            long traceId,
            long authorization,
            DirectBuffer params,
            int paramsOffset,
            int paramsLength)
        {
        }

        protected void doNetBegin(
            long traceId,
            long authorization,
            HttpBeginExFW httpBeginEx,
            int bodyLength)
        {
            state = McpState.openingInitial(state);

            net = newStream(this::onNetMessage,
                originId, routedId, initialId,
                0, 0, 0,
                traceId, authorization, affinity,
                httpBeginEx);

            if (net != null)
            {
                replyMax = decodeMax;
                doNetWindow(traceId, authorization, 0L, 0);

                if (bodyLength > 0)
                {
                    encodeAndEnd(traceId, authorization, bodyLength);
                }
            }
        }

        protected void encodeAndEnd(
            long traceId,
            long authorization,
            int bodyLength)
        {
            if (initialMax > 0)
            {
                encodeNet(traceId, authorization, codecBuffer, 0, bodyLength);
                doNetEnd(traceId, authorization);
            }
            else
            {
                encodeSlot = bufferPool.acquire(initialId);

                if (encodeSlot == NO_SLOT)
                {
                    cleanup(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, codecBuffer, 0, bodyLength);
                    encodeSlotOffset = bodyLength;
                    encodeSlotTraceId = traceId;
                    encodeSlotAuthorization = authorization;
                }
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (net != null && !McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, traceId, authorization);
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (net != null && !McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId, traceId, authorization);
            }
        }

        protected void bufferResponseData(
            OctetsFW payload)
        {
            if (payload != null && payload.sizeof() > 0)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(initialId);
                }

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer buf = bufferPool.buffer(decodeSlot);
                    buf.putBytes(decodeSlotOffset, payload.buffer(), payload.offset(), payload.sizeof());
                    decodeSlotOffset += payload.sizeof();
                }
            }
        }

        protected void flushResponseToApp(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final DirectBuffer buf = bufferPool.buffer(decodeSlot);
                final int resultStart = findResultStart(buf, decodeSlotOffset);
                if (resultStart >= 0)
                {
                    final int resultLength = decodeSlotOffset - resultStart - 1;
                    mcp.doAppData(traceId, buf, resultStart, resultLength);
                }
                cleanupResponseSlot();
            }
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int reserved = length;

            doData(net, originId, routedId, initialId,
                traceId, authorization,
                DATA_FLAGS_COMPLETE, 0, reserved,
                buffer, offset, length);
        }

        private void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(net, originId, routedId, initialId, traceId, authorization);
            }
        }

        private void cleanupEncodeSlot()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
                encodeSlotAuthorization = 0;
            }
        }

        private void cleanupResponseSlot()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        private void cleanup(
            long traceId,
            long authorization)
        {
            cleanupEncodeSlot();
            cleanupResponseSlot();
            if (net != null)
            {
                doAbort(net, originId, routedId, initialId, traceId, authorization);
            }
            mcp.doAppAbort(traceId);
        }
    }

    private final class HttpInitializeRequest extends HttpStream
    {
        private String responseSessionId;

        HttpInitializeRequest(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," +
                "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
                "\"clientInfo\":{\"name\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, clientName);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\",\"version\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, clientVersion);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            responseSessionId = mcp.sessionId;

            final OctetsFW ext = begin.extension();
            if (ext.sizeof() > 0)
            {
                final HttpBeginExFW httpBeginEx = httpBeginExRO.tryWrap(
                    ext.buffer(), ext.offset(), ext.limit());
                if (httpBeginEx != null)
                {
                    final HttpHeaderFW sessionHeader = httpBeginEx.headers()
                        .matchFirst(h -> HTTP_HEADER_SESSION.equals(h.name().asString()));
                    if (sessionHeader != null)
                    {
                        responseSessionId = sessionHeader.value().asString();
                    }
                }
            }

        }

        @Override
        void onNetDataImpl(
            DataFW data)
        {
        }

        @Override
        void onNetEndImpl(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            final long netInitialId2 = supplyInitialId.applyAsLong(routedId);
            final HttpNotifyInitialized notify = new HttpNotifyInitialized(
                mcp, responseSessionId);
            mcp.http = notify;
            notify.doNetBegin(traceId, authorization);
        }
    }

    private final class HttpNotifyInitialized extends HttpStream
    {
        private final String sessionId;

        HttpNotifyInitialized(
            McpStream mcp,
            String sessionId)
        {
            super(mcp);
            this.sessionId = sessionId;
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (sessionId != null)
            {
                final String sid = sessionId;
                builder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = builder.build();

            final int bodyLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            doNetBegin(traceId, authorization, httpBeginEx, bodyLength);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
        }

        @Override
        void onNetDataImpl(
            DataFW data)
        {
        }

        @Override
        void onNetEndImpl(
            EndFW end)
        {
            final long traceId = end.traceId();
            final String sid = sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .lifecycle(b -> b.sessionId(sid))
                .build();
            mcp.doAppBegin(traceId, beginEx);
        }
    }

    private abstract class HttpRequestStream extends HttpStream
    {
        protected final McpRequestStream request;

        HttpRequestStream(
            McpRequestStream mcp)
        {
            super(mcp);
            this.request = mcp;
        }

        @Override
        protected void doNotifyCancelled(
            long traceId)
        {
            if (sessions.containsKey(mcp.sessionId))
            {

                new HttpNotifyCancelled(request).doNetBegin(traceId);
            }
        }

        @Override
        void onNetDataImpl(
            DataFW data)
        {
            bufferResponseData(data.payload());
        }

        @Override
        void onNetEndImpl(
            EndFW end)
        {
            final long traceId = end.traceId();
            flushResponseToApp(traceId);
            mcp.doAppEnd(traceId);
        }
    }

    private final class HttpToolsListStream extends HttpRequestStream
    {
        HttpToolsListStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"tools/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .toolsList(b -> b.sessionId(sid))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpToolsCallStream extends HttpRequestStream
    {
        private final String toolName;

        HttpToolsCallStream(
            McpRequestStream mcp,
            String toolName)
        {
            super(mcp);
            this.toolName = toolName;
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            doNetBegin(traceId, authorization, httpBeginEx, 0);
        }

        @Override
        void doNetBodyAndEnd(
            long traceId,
            long authorization,
            DirectBuffer params,
            int paramsOffset,
            int paramsLength)
        {
            if (net != null)
            {
                int pos = 0;
                pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
                pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
                pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"tools/call\",\"params\":");
                codecBuffer.putBytes(pos, params, paramsOffset, paramsLength);
                pos += paramsLength;
                codecBuffer.putByte(pos++, (byte) '}');
                encodeAndEnd(traceId, authorization, pos);
            }
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final String name = toolName;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .toolsCall(b -> b.sessionId(sid).name(name))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpPromptsListStream extends HttpRequestStream
    {
        HttpPromptsListStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"prompts/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .promptsList(b -> b.sessionId(sid))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpPromptsGetStream extends HttpRequestStream
    {
        private final String promptName;

        HttpPromptsGetStream(
            McpRequestStream mcp,
            String promptName)
        {
            super(mcp);
            this.promptName = promptName;
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                ",\"method\":\"prompts/get\",\"params\":{\"name\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, promptName);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final String name = promptName;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .promptsGet(b -> b.sessionId(sid).name(name))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpResourcesListStream extends HttpRequestStream
    {
        HttpResourcesListStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"resources/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resourcesList(b -> b.sessionId(sid))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpResourcesReadStream extends HttpRequestStream
    {
        private final String resourceUri;

        HttpResourcesReadStream(
            McpRequestStream mcp,
            String resourceUri)
        {
            super(mcp);
            this.resourceUri = resourceUri;
        }

        @Override
        void doNetBegin(
            long traceId,
            long authorization)
        {
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            if (mcp.sessionId != null)
            {
                final String sid = mcp.sessionId;
                extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));
            }

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, request.assignedRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                ",\"method\":\"resources/read\",\"params\":{\"uri\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, resourceUri);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}");

            doNetBegin(traceId, authorization, httpBeginEx, pos);
        }

        @Override
        void onNetBeginImpl(
            BeginFW begin)
        {
            final String sid = mcp.sessionId;
            final String uri = resourceUri;
            final McpBeginExFW beginEx = mcpBeginExRW.wrap(codecBuffer, 0, codecBuffer.capacity())
                .typeId(mcpTypeId)
                .resourcesRead(b -> b.sessionId(sid).uri(uri))
                .build();
            mcp.doAppBegin(begin.traceId(), beginEx);
            if (mcp.appClosedEmpty)
            {
                doNotifyCancelled(begin.traceId());
            }
        }
    }

    private final class HttpTerminateSession
    {
        private final long initialId;
        private final long replyId;
        private final McpStream mcp;

        private MessageConsumer net;
        private int initialMax;
        private boolean endSent;

        HttpTerminateSession(
            McpStream mcp)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.mcp = mcp;
        }

        void doNetBegin(
            long traceId,
            long authorization)
        {
            final String sid = mcp.sessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value("DELETE"))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            net = newStream(this::onNetMessage, mcp.originId, mcp.resolvedId, initialId,
                0, 0, 0, traceId, authorization, mcp.affinity, httpBeginEx);

            if (net != null && initialMax > 0)
            {
                endSent = true;
                doEnd(net, mcp.originId, mcp.resolvedId, initialId, traceId, authorization);
            }
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
                doWindow(net, mcp.originId, mcp.resolvedId, replyId,
                    begin.traceId(), mcp.authorization, 0, writeBuffer.capacity(), 0);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                initialMax = window.maximum();
                if (!endSent && initialMax > 0)
                {
                    endSent = true;
                    doEnd(net, mcp.originId, mcp.resolvedId, initialId,
                        window.traceId(), mcp.authorization);
                }
                break;
            case DataFW.TYPE_ID:
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                mcp.doAppEnd(end.traceId());
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                mcp.doAppAbort(abort.traceId());
                break;
            default:
                break;
            }
        }
    }

    private final class HttpNotifyCancelled
    {
        private final long initialId;
        private final long replyId;
        private final String sessionId;
        private final int cancelledRequestId;
        private final long authorization;
        private final long originId;
        private final long resolvedId;
        private final long affinity;

        private MessageConsumer net;
        private boolean bodySent;

        HttpNotifyCancelled(
            McpRequestStream mcp)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.sessionId = mcp.sessionId;
            this.cancelledRequestId = mcp.assignedRequestId;
            this.authorization = mcp.authorization;
            this.originId = mcp.originId;
            this.resolvedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
        }

        void doNetBegin(
            long traceId)
        {
            final String sid = sessionId;
            final HttpBeginExFW httpBeginEx = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId)
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_PATH).value(MCP_PATH))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            net = newStream(this::onNetMessage, originId, resolvedId, initialId,
                0, 0, 0, traceId, authorization, affinity, httpBeginEx);
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
                doWindow(net, originId, resolvedId, replyId,
                    begin.traceId(), authorization, 0, writeBuffer.capacity(), 0);
                break;
            case WindowFW.TYPE_ID:
                if (!bodySent)
                {
                    bodySent = true;
                    final WindowFW window = windowRO.wrap(buffer, index, index + length);
                    sendBody(window.traceId());
                }
                break;
            default:
                break;
            }
        }

        private void sendBody(
            long traceId)
        {
            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":");
            pos += codecBuffer.putIntAscii(pos, cancelledRequestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"reason\":\"User cancelled\"}}");

            doData(net, originId, resolvedId, initialId,
                traceId, authorization, DATA_FLAGS_COMPLETE, 0, pos, codecBuffer, 0, pos);
            doEnd(net, originId, resolvedId, initialId, traceId, authorization);
        }
    }

    private int findResultStart(
        DirectBuffer buffer,
        int length)
    {
        inputRO.wrap(buffer, 0, length);
        final JsonParser parser = StreamingJson.createParser(inputRO);
        int depth = 0;
        int resultStart = -1;
        decode:
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
                depth++;
                break;
            case END_OBJECT:
                depth--;
                break;
            case KEY_NAME:
                if (depth == 1 && "result".equals(parser.getString()))
                {
                    final JsonParser.Event valueEvent = parser.next();
                    final int after = (int) parser.getLocation().getStreamOffset();
                    switch (valueEvent)
                    {
                    case START_OBJECT:
                    case START_ARRAY:
                        resultStart = after - 1;
                        break;
                    case VALUE_STRING:
                        resultStart = -1;
                        break;
                    default:
                        resultStart = -1;
                        break;
                    }
                    break decode;
                }
                break;
            default:
                break;
            }
        }
        return resultStart;
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
        HttpBeginExFW extension)
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

        if (receiver != null)
        {
            receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

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
        DirectBuffer extBuffer,
        int extOffset,
        int extLength)
    {
        final BeginFW.Builder builder = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(originId)
            .routedId(routedId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity);
        if (extBuffer != null && extLength > 0)
        {
            builder.extension(extBuffer, extOffset, extLength);
        }
        final BeginFW begin = builder.build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        DirectBuffer payload,
        int offset,
        int length)
    {
        doData(receiver, originId, routedId, streamId, 0L, 0L, 0,
            traceId, authorization, flags, budgetId, reserved, payload, offset, length);
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
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
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
        doEnd(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
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
        long traceId,
        long authorization)
    {
        doAbort(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
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

    private void doReset(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        long authorization)
    {
        doReset(receiver, originId, routedId, streamId, 0L, 0L, 0, traceId, authorization);
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
        long traceId,
        long authorization,
        long budgetId,
        int credit,
        int padding)
    {
        doWindow(receiver, originId, routedId, streamId, 0L, 0L, credit,
            traceId, authorization, budgetId, padding);
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
}
