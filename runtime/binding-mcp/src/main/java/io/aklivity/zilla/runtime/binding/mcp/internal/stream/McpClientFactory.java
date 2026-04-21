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
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.Flyweight;
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
    private static final String HTTP_HEADER_CONTENT_TYPE = "content-type";
    private static final String HTTP_HEADER_SESSION = "mcp-session-id";
    private static final String HTTP_HEADER_MCP_VERSION = "mcp-protocol-version";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String MCP_PROTOCOL_VERSION = "2025-11-25";
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

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpStream> sessions = new Object2ObjectHashMap<>();
    private final Int2ObjectHashMap<McpLifecycleResolver> resolvers;
    private final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories;

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

        final Int2ObjectHashMap<McpLifecycleResolver> resolvers = new Int2ObjectHashMap<>();
        resolvers.put(McpBeginExFW.KIND_TOOLS_LIST,
            beginEx -> lookupSession(beginEx.toolsList().sessionId().asString()));
        resolvers.put(McpBeginExFW.KIND_TOOLS_CALL,
            beginEx -> lookupSession(beginEx.toolsCall().sessionId().asString()));
        resolvers.put(McpBeginExFW.KIND_PROMPTS_LIST,
            beginEx -> lookupSession(beginEx.promptsList().sessionId().asString()));
        resolvers.put(McpBeginExFW.KIND_PROMPTS_GET,
            beginEx -> lookupSession(beginEx.promptsGet().sessionId().asString()));
        resolvers.put(McpBeginExFW.KIND_RESOURCES_LIST,
            beginEx -> lookupSession(beginEx.resourcesList().sessionId().asString()));
        resolvers.put(McpBeginExFW.KIND_RESOURCES_READ,
            beginEx -> lookupSession(beginEx.resourcesRead().sessionId().asString()));
        this.resolvers = resolvers;

        final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories = new Int2ObjectHashMap<>();
        requestFactories.put(McpBeginExFW.KIND_TOOLS_LIST,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpToolsListStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session));
        requestFactories.put(McpBeginExFW.KIND_TOOLS_CALL,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpToolsCallStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session,
                    beginEx.toolsCall().name().asString()));
        requestFactories.put(McpBeginExFW.KIND_PROMPTS_LIST,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpPromptsListStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session));
        requestFactories.put(McpBeginExFW.KIND_PROMPTS_GET,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpPromptsGetStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session,
                    beginEx.promptsGet().name().asString()));
        requestFactories.put(McpBeginExFW.KIND_RESOURCES_LIST,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpResourcesListStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session));
        requestFactories.put(McpBeginExFW.KIND_RESOURCES_READ,
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) ->
                new McpResourcesReadStream(sender, originId, routedId, initialId, resolvedId,
                    affinity, authorization, session,
                    beginEx.resourcesRead().uri().asString()));
        this.requestFactories = requestFactories;
    }

    private McpLifecycleStream lookupSession(
        String sessionId)
    {
        final McpStream session = sessions.get(sessionId);
        return session instanceof McpLifecycleStream ? (McpLifecycleStream) session : null;
    }

    @FunctionalInterface
    private interface McpLifecycleResolver
    {
        McpLifecycleResolver DEFAULT = beginEx -> null;

        McpLifecycleStream resolveSession(McpBeginExFW beginEx);
    }

    @FunctionalInterface
    private interface McpRequestStreamFactory
    {
        McpRequestStreamFactory DEFAULT =
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization, beginEx) -> null;

        McpRequestStream newRequest(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpBeginExFW mcpBeginEx);
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

                if (mcpBeginEx.kind() == McpBeginExFW.KIND_LIFECYCLE)
                {
                    newStream = new McpLifecycleStream(
                        sender, originId, routedId, initialId, route.id, affinity, authorization,
                        mcpBeginEx.lifecycle().sessionId().asString(), route.with)::onAppMessage;
                }
                else
                {
                    final McpLifecycleResolver resolver = resolvers.getOrDefault(
                        mcpBeginEx.kind(), McpLifecycleResolver.DEFAULT);
                    final McpLifecycleStream session = resolver.resolveSession(mcpBeginEx);
                    if (session != null)
                    {
                        final McpRequestStreamFactory requestFactory = requestFactories.getOrDefault(
                            mcpBeginEx.kind(), McpRequestStreamFactory.DEFAULT);
                        final McpRequestStream request = requestFactory.newRequest(
                            session, sender, originId, routedId, initialId, route.id,
                            affinity, authorization, mcpBeginEx);
                        if (request != null)
                        {
                            newStream = request::onAppMessage;
                        }
                    }
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
        protected final McpWithConfig with;

        protected HttpStream http;

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
            String sessionId,
            McpWithConfig with)
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
            this.with = with;
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

            onAppBeginImpl(traceId);

            doAppWindow(traceId, authorization, 0L, 0);
        }

        abstract void onAppBeginImpl(long traceId);

        abstract void onAppEndImpl(long traceId);

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
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
                http.doNetData(traceId, authorization,
                    payload.buffer(), payload.offset(), payload.limit());
            }

            doAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            onAppEndImpl(traceId);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            onAppAbortImpl(traceId);
        }

        void onAppAbortImpl(
            long traceId)
        {
            http.doNotifyCancelled(traceId);
            http.doNetAbort(traceId, authorization);
        }

        private void onAppFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;
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

            state = McpState.openedReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBud = window.budgetId();
            replyPad = window.padding();

            assert replyAck <= replySeq;
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            onAppResetImpl(traceId);
        }

        void onAppResetImpl(
            long traceId)
        {
            http.doNotifyCancelled(traceId);
            http.doNetReset(traceId, authorization);
        }

        void doAppBegin(
            long traceId,
            McpBeginExFW beginEx)
        {
            state = McpState.openingReply(state);

            doBegin(sender, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, affinity,
                beginEx);
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
        private final Int2ObjectHashMap<McpRequestStream> requests = new Int2ObjectHashMap<>();

        private int nextRequestId = 2;

        McpLifecycleStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            String sessionId,
            McpWithConfig with)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, sessionId, with);
            sessions.put(sessionId, this);
            this.http = new HttpInitializeRequest(this);
        }

        int register(
            McpRequestStream request)
        {
            final int id = nextRequestId++;
            requests.put(id, request);
            return id;
        }

        void unregister(
            int id)
        {
            requests.remove(id);
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
            terminateSession(traceId);
        }

        @Override
        void onAppAbortImpl(
            long traceId)
        {
            terminateSession(traceId);
        }

        @Override
        void onAppResetImpl(
            long traceId)
        {
            terminateSession(traceId);
        }

        private void terminateSession(
            long traceId)
        {
            sessions.remove(sessionId);

            for (McpRequestStream request : requests.values())
            {
                request.doAppAbort(traceId);
                request.http.doNetAbort(traceId, authorization);
            }
            requests.clear();

            new HttpTerminateSession(this).doNetBegin(traceId, authorization);
        }
    }

    private abstract class McpRequestStream extends McpStream
    {
        final McpLifecycleStream session;

        McpRequestStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization,
                session.sessionId, session.with);
            this.session = session;
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
            http.doNetEnd(traceId, authorization);
            ((HttpRequestStream) http).doUnregister();
        }
    }

    private abstract class McpRequestManyStream extends McpRequestStream
    {
        McpRequestManyStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
        }
    }

    private abstract class McpRequestOneStream extends McpRequestStream
    {
        McpRequestOneStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
        }
    }

    private final class McpToolsListStream extends McpRequestManyStream
    {
        McpToolsListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
            this.http = new HttpToolsListStream(this);
        }
    }

    private final class McpToolsCallStream extends McpRequestOneStream
    {
        McpToolsCallStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session,
            String toolName)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
            this.http = new HttpToolsCallStream(this, toolName);
        }
    }

    private final class McpPromptsListStream extends McpRequestManyStream
    {
        McpPromptsListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
            this.http = new HttpPromptsListStream(this);
        }
    }

    private final class McpPromptsGetStream extends McpRequestOneStream
    {
        McpPromptsGetStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session,
            String promptName)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
            this.http = new HttpPromptsGetStream(this, promptName);
        }
    }

    private final class McpResourcesListStream extends McpRequestManyStream
    {
        McpResourcesListStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
            this.http = new HttpResourcesListStream(this);
        }
    }

    private final class McpResourcesReadStream extends McpRequestOneStream
    {
        McpResourcesReadStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization,
            McpLifecycleStream session,
            String resourceUri)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, authorization, session);
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

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        protected int decodeSlot = NO_SLOT;
        protected int decodeSlotOffset;

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
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.openedReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = decodeMax;

            assert replyAck <= replySeq;

            onNetBeginImpl(begin);
            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void onNetData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + data.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;

            onNetDataImpl(data);
            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void onNetFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence + flush.reserved();
            replyAck = replySeq;

            assert replyAck <= replySeq;
            doNetWindow(traceId, authorization, 0L, 0);
        }

        private void doNetWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            doWindow(net, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, budgetId, padding);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.closedReply(state);
            cleanupEncodeSlot();
            cleanupDecodeSlot();
            mcp.doAppAbort(traceId);
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
            initialAck = acknowledge;
            initialMax = maximum;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                final int limit = encodeSlotOffset;
                final long traceId = encodeSlotTraceId;
                final long authorization = encodeSlotAuthorization;

                encodeSlotOffset = 0;
                encodeNet(traceId, authorization, encodeBuffer, 0, limit);
            }
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.closedInitial(state);
            cleanupEncodeSlot();
            cleanupDecodeSlot();
            mcp.doAppReset(traceId);
        }

        abstract void onNetBeginImpl(BeginFW begin);

        abstract void onNetDataImpl(DataFW data);

        abstract void onNetEndImpl(EndFW end);

        abstract void doNetBegin(long traceId, long authorization);

        protected void doNetBegin(
            long traceId,
            long authorization,
            HttpBeginExFW httpBeginEx)
        {
            state = McpState.openingInitial(state);

            net = newStream(this::onNetMessage,
                originId, routedId, initialId,
                0, 0, 0,
                traceId, authorization, affinity,
                httpBeginEx);

            assert net != null;

            replyMax = decodeMax;
            doNetWindow(traceId, authorization, 0L, 0);
        }

        void doNetData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;
                encodeSlotAuthorization = authorization;

                buffer = encodeBuffer;
                offset = 0;
                limit = encodeSlotOffset;
            }

            encodeNet(traceId, authorization, buffer, offset, limit);
        }

        void doNetEnd(
            long traceId,
            long authorization)
        {
            state = McpState.closingInitial(state);
            if (encodeSlot == NO_SLOT && !McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(net, originId, routedId, initialId, traceId, authorization);
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId, traceId, authorization);
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId, traceId, authorization);
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
                cleanupDecodeSlot();
            }
        }

        private void encodeNet(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int maxLength = limit - offset;
            final int initialWin = initialMax - (int)(initialSeq - initialAck);
            final int length = Math.max(Math.min(initialWin, maxLength), 0);

            if (length > 0)
            {
                final int reserved = length;

                doData(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization,
                    DATA_FLAGS_COMPLETE, 0L, reserved,
                    buffer, offset, length);

                initialSeq += reserved;
            }

            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = bufferPool.acquire(initialId);
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanup(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                    encodeSlotTraceId = traceId;
                    encodeSlotAuthorization = authorization;
                }
            }
            else
            {
                cleanupEncodeSlot();

                if (McpState.initialClosing(state))
                {
                    doNetEnd(traceId, authorization);
                }
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

        private void cleanupDecodeSlot()
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
            cleanupDecodeSlot();
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
            final HttpBeginExFW.Builder extBuilder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));
            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," +
                "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," +
                "\"clientInfo\":{\"name\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, clientName);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\",\"version\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, clientVersion);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = sessionId;
            builder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = builder.build();

            final int bodyLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, bodyLength);
            doNetEnd(traceId, authorization);
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
        protected final int requestId;

        HttpRequestStream(
            McpRequestStream mcp)
        {
            super(mcp);
            this.request = mcp;
            this.requestId = mcp.session.register(mcp);
        }

        void doUnregister()
        {
            request.session.unregister(requestId);
        }

        @Override
        protected void doNotifyCancelled(
            long traceId)
        {
            if (sessions.containsKey(mcp.sessionId))
            {
                new HttpNotifyCancelled(this).doNetBegin(traceId);
            }
        }

        @Override
        void onNetDataImpl(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"tools/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            doNetBegin(traceId, authorization, httpBeginEx);

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"tools/call\",\"params\":");
            doNetData(traceId, authorization, codecBuffer, 0, pos);
        }

        @Override
        void doNetEnd(
            long traceId,
            long authorization)
        {
            final int pos = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            super.doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"prompts/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                ",\"method\":\"prompts/get\",\"params\":{\"name\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, promptName);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, ",\"method\":\"resources/list\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    extBuilder.headersItem(h -> h.name(name).value(value)));
            }
            extBuilder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION));

            final String sid = mcp.sessionId;
            extBuilder.headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid));

            final HttpBeginExFW httpBeginEx = extBuilder.build();

            int pos = 0;
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "{\"jsonrpc\":\"2.0\",\"id\":");
            pos += codecBuffer.putIntAscii(pos, requestId);
            pos += codecBuffer.putStringWithoutLengthAscii(pos,
                ",\"method\":\"resources/read\",\"params\":{\"uri\":\"");
            pos += codecBuffer.putStringWithoutLengthAscii(pos, resourceUri);
            pos += codecBuffer.putStringWithoutLengthAscii(pos, "\"}}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, pos);
            doNetEnd(traceId, authorization);
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
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value("DELETE"))
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
                onNetBegin(begin);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            case DataFW.TYPE_ID:
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            doWindow(net, mcp.originId, mcp.resolvedId, replyId,
                begin.traceId(), mcp.authorization, 0, writeBuffer.capacity(), 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            initialMax = window.maximum();
            if (!endSent && initialMax > 0)
            {
                endSent = true;
                doEnd(net, mcp.originId, mcp.resolvedId, initialId,
                    window.traceId(), mcp.authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            mcp.doAppEnd(end.traceId());
        }

        private void onNetAbort(
            AbortFW abort)
        {
            mcp.doAppAbort(abort.traceId());
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
        private final McpWithConfig with;

        private MessageConsumer net;
        private boolean bodySent;

        HttpNotifyCancelled(
            HttpRequestStream http)
        {
            final McpRequestStream mcp = http.request;
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.sessionId = mcp.sessionId;
            this.cancelledRequestId = http.requestId;
            this.authorization = mcp.authorization;
            this.originId = mcp.originId;
            this.resolvedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
            this.with = mcp.with;
        }

        void doNetBegin(
            long traceId)
        {
            final String sid = sessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (with != null && with.headers != null)
            {
                with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
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
                onNetBegin(begin);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetWindow(window);
                break;
            default:
                break;
            }
        }

        private void onNetBegin(
            BeginFW begin)
        {
            doWindow(net, originId, resolvedId, replyId,
                begin.traceId(), authorization, 0, writeBuffer.capacity(), 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            if (!bodySent)
            {
                bodySent = true;
                final long traceId = window.traceId();

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
        Flyweight extension)
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
        if (extension != null && extension.sizeof() > 0)
        {
            builder.extension(extension.buffer(), extension.offset(), extension.sizeof());
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
