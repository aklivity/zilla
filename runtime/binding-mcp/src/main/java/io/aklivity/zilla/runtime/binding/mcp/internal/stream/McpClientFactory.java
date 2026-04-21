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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
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
    private final int encodeMax;
    private final String clientName;
    private final String clientVersion;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Map<String, McpStream> sessions = new Object2ObjectHashMap<>();
    private final Int2ObjectHashMap<McpSessionIdResolver> resolvers;
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
        this.encodeMax = bufferPool.slotCapacity();
        this.clientName = config.clientName();
        this.clientVersion = config.clientVersion();

        final Int2ObjectHashMap<McpSessionIdResolver> resolvers = new Int2ObjectHashMap<>();
        resolvers.put(KIND_TOOLS_LIST, ex -> ex.toolsList().sessionId().asString());
        resolvers.put(KIND_TOOLS_CALL, ex -> ex.toolsCall().sessionId().asString());
        resolvers.put(KIND_PROMPTS_LIST, ex -> ex.promptsList().sessionId().asString());
        resolvers.put(KIND_PROMPTS_GET, ex -> ex.promptsGet().sessionId().asString());
        resolvers.put(KIND_RESOURCES_LIST, ex -> ex.resourcesList().sessionId().asString());
        resolvers.put(KIND_RESOURCES_READ, ex -> ex.resourcesRead().sessionId().asString());
        this.resolvers = resolvers;

        final Int2ObjectHashMap<McpRequestStreamFactory> requestFactories = new Int2ObjectHashMap<>();
        requestFactories.put(KIND_TOOLS_LIST, McpToolsListStream::new);
        requestFactories.put(KIND_TOOLS_CALL, McpToolsCallStream::new);
        requestFactories.put(KIND_PROMPTS_LIST, McpPromptsListStream::new);
        requestFactories.put(KIND_PROMPTS_GET, McpPromptsGetStream::new);
        requestFactories.put(KIND_RESOURCES_LIST, McpResourcesListStream::new);
        requestFactories.put(KIND_RESOURCES_READ, McpResourcesReadStream::new);
        this.requestFactories = requestFactories;
    }

    private McpLifecycleStream lookupSession(
        String sessionId)
    {
        final McpStream session = sessions.get(sessionId);
        return session instanceof McpLifecycleStream ? (McpLifecycleStream) session : null;
    }

    @FunctionalInterface
    private interface McpSessionIdResolver
    {
        McpSessionIdResolver DEFAULT = beginEx -> null;

        String resolveSessionId(McpBeginExFW beginEx);
    }

    @FunctionalInterface
    private interface McpRequestStreamFactory
    {
        McpRequestStreamFactory DEFAULT =
            (session, sender, originId, routedId, initialId, resolvedId, affinity, authorization) -> null;

        McpRequestStream newRequest(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization);
    }

    @FunctionalInterface
    private interface HttpResponseDecoder
    {
        int decode(
            HttpRequestStream http,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private final HttpResponseDecoder decodeJsonRpc = this::decodeJsonRpc;
    private final HttpResponseDecoder decodeJsonRpcStart = this::decodeJsonRpcStart;
    private final HttpResponseDecoder decodeJsonRpcNext = this::decodeJsonRpcNext;
    private final HttpResponseDecoder decodeJsonRpcResult = this::decodeJsonRpcResult;
    private final HttpResponseDecoder decodeIgnore = this::decodeIgnore;

    private int decodeJsonRpc(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        input.wrap(buffer, offset, limit - offset);

        http.decodableJson = StreamingJson.createParser(input);
        http.decoder = decodeJsonRpcStart;

        return limit - input.available();
    }

    private int decodeJsonRpcStart(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        final JsonParser parser = http.decodableJson;
        int progress = offset;
        if (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            http.decoder = event == JsonParser.Event.START_OBJECT
                ? decodeJsonRpcNext
                : decodeIgnore;
            progress = limit - input.available();
        }
        return progress;
    }

    private int decodeJsonRpcNext(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        final JsonParser parser = http.decodableJson;
        int progress = offset;
        while (parser.hasNext())
        {
            final JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.KEY_NAME)
            {
                if ("result".equals(parser.getString()))
                {
                    http.decoder = decodeJsonRpcResult;
                    progress = limit - input.available();
                    break;
                }
                if (parser.hasNext())
                {
                    parser.next();
                }
            }
            else if (event == JsonParser.Event.END_OBJECT)
            {
                http.decoder = decodeIgnore;
                progress = limit - input.available();
                break;
            }
        }
        return progress;
    }

    private int decodeJsonRpcResult(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        DirectBufferInputStreamEx input = inputRO;
        final JsonParser parser = http.decodableJson;
        int progress = offset;
        if (parser.hasNext())
        {
            final JsonParser.Event valueEvent = parser.next();
            final int after = (int) parser.getLocation().getStreamOffset();
            if (valueEvent == JsonParser.Event.START_OBJECT ||
                valueEvent == JsonParser.Event.START_ARRAY)
            {
                http.decodedResultStart = after - 1;
            }
            http.decoder = decodeIgnore;
            progress = limit - input.available();
        }
        return progress;
    }

    private int decodeIgnore(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
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

                if (mcpBeginEx.kind() == KIND_LIFECYCLE)
                {
                    newStream = new McpLifecycleStream(
                        sender, originId, routedId, initialId, route.id, affinity,
                        mcpBeginEx.lifecycle().sessionId().asString(), route.with)::onAppMessage;
                }
                else
                {
                    final McpSessionIdResolver resolver = resolvers.getOrDefault(
                        mcpBeginEx.kind(), McpSessionIdResolver.DEFAULT);
                    final String sessionId = resolver.resolveSessionId(mcpBeginEx);
                    final McpLifecycleStream session = lookupSession(sessionId);
                    if (session != null)
                    {
                        final McpRequestStreamFactory requestFactory = requestFactories.getOrDefault(
                            mcpBeginEx.kind(), McpRequestStreamFactory.DEFAULT);
                        final McpRequestStream request = requestFactory.newRequest(
                            session, sender, originId, routedId, initialId, route.id,
                            affinity, authorization);
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

        int state;

        McpStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
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
            final long authorization = begin.authorization();

            state = McpState.openingInitial(state);
            state = McpState.openedInitial(state);

            initialSeq = begin.sequence();
            initialAck = begin.acknowledge();
            initialMax = encodeMax;

            final OctetsFW extension = begin.extension();
            final McpBeginExFW mcpBeginEx = extension.sizeof() > 0
                ? mcpBeginExRO.wrap(extension.buffer(), extension.offset(), extension.limit())
                : null;

            onAppBeginImpl(traceId, authorization, mcpBeginEx);

            doAppWindow(traceId, authorization, 0L, 0);
        }

        abstract void onAppBeginImpl(long traceId, long authorization, McpBeginExFW mcpBeginEx);

        private void onAppData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            final OctetsFW payload = data.payload();
            if (payload != null && payload.sizeof() > 0)
            {
                http.doEncodeRequestData(traceId, authorization,
                    payload.buffer(), payload.offset(), payload.limit());
            }

            flushAppWindow(traceId, authorization, 0L, 0);
        }

        private void onAppEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            http.doEncodeRequestEnd(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        private void onAppAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            http.doNetAbort(traceId, authorization);

            onAppClosed(traceId, authorization);
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
            final long traceId = window.traceId();
            final long authorization = window.authorization();
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

            if (http != null)
            {
                http.onAppReplyWindow(traceId, authorization);
            }
        }

        private void onAppReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;

            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            http.doNetReset(traceId, authorization);

            onAppClosed(traceId, authorization);
        }

        void onAppClosed(
            long traceId,
            long authorization)
        {
        }

        void onNetBegin(
            BeginFW begin)
        {
        }

        void onNetData(
            DataFW data)
        {
        }

        void onNetEnd(
            EndFW end)
        {
        }

        void doAppBegin(
            long traceId,
            long authorization,
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
            long authorization,
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
            long traceId,
            long authorization)
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
            long traceId,
            long authorization)
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
            long traceId,
            long authorization)
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

        void flushAppWindow(
            long traceId,
            long authorization,
            long budgetId,
            int padding)
        {
            final int pending = http != null ? http.encodeSlotOffset : 0;
            final long newAck = Math.max(initialSeq - pending, initialAck);
            if (newAck > initialAck)
            {
                initialAck = newAck;
                assert initialAck <= initialSeq;
                doAppWindow(traceId, authorization, budgetId, padding);
            }
        }
    }

    private final class McpLifecycleStream extends McpStream
    {
        private final Int2ObjectHashMap<McpRequestStream> requests = new Int2ObjectHashMap<>();

        String responseSessionId;
        private int nextRequestId = 2;

        McpLifecycleStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            String sessionId,
            McpWithConfig with)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity, sessionId, with);
            sessions.put(sessionId, this);
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
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpInitializeRequest(this);
            http.doEncodeRequestBegin(traceId, authorization);
            http.doEncodeRequestEnd(traceId, authorization);
        }

        @Override
        void onNetBegin(
            BeginFW begin)
        {
            if (responseSessionId == null)
            {
                responseSessionId = sessionId;

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
        }

        @Override
        void onNetData(
            DataFW data)
        {
            http.doReplyConsumed(data.traceId(), data.authorization(), data.reserved());
        }

        @Override
        void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();

            if (http instanceof HttpInitializeRequest)
            {
                final HttpNotifyInitialized notify = new HttpNotifyInitialized(this);
                this.http = notify;
                notify.doEncodeRequestBegin(traceId, authorization);
                notify.doEncodeRequestEnd(traceId, authorization);
            }
            else
            {
                final String sid = responseSessionId;
                doAppBegin(traceId, authorization, mcpBeginExRW
                    .wrap(codecBuffer, 0, codecBuffer.capacity())
                    .typeId(mcpTypeId)
                    .lifecycle(b -> b.sessionId(sid))
                    .build());
            }
        }

        @Override
        void onAppClosed(
            long traceId,
            long authorization)
        {
            if (sessions.remove(sessionId) != null)
            {
                for (McpRequestStream request : requests.values())
                {
                    request.doAppAbort(traceId, authorization);
                    request.http.doNetAbort(traceId, authorization);
                }
                requests.clear();

                new HttpTerminateSession(this).doNetBegin(traceId, authorization);
            }
        }
    }

    private abstract class McpRequestStream extends McpStream
    {
        final McpLifecycleStream session;
        final int requestId;

        McpRequestStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity)
        {
            super(sender, originId, routedId, initialId, resolvedId, affinity,
                session.sessionId, session.with);
            this.session = session;
            this.requestId = session.register(this);
        }

        @Override
        final void onAppClosed(
            long traceId,
            long authorization)
        {
            if (McpState.closed(state) && session.requests.remove(requestId) != null)
            {
                new HttpNotifyCancelled(this).doNetBegin(traceId, authorization);
            }
        }

        @Override
        final void onNetBegin(
            BeginFW begin)
        {
            doAppBegin(begin.traceId(), begin.authorization(), null);
        }

        @Override
        final void onNetData(
            DataFW data)
        {
            ((HttpRequestStream) http).onNetResponseData(data);
        }

        @Override
        final void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            ((HttpRequestStream) http).flushResponseToApp(traceId, authorization);
            session.unregister(requestId);
        }
    }

    private final class McpToolsListStream extends McpRequestStream
    {
        McpToolsListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpToolsListStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
        }
    }

    private final class McpToolsCallStream extends McpRequestStream
    {
        McpToolsCallStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpToolsCallStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
        }
    }

    private final class McpPromptsListStream extends McpRequestStream
    {
        McpPromptsListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpPromptsListStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
        }
    }

    private final class McpPromptsGetStream extends McpRequestStream
    {
        McpPromptsGetStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpPromptsGetStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
        }
    }

    private final class McpResourcesListStream extends McpRequestStream
    {
        McpResourcesListStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpResourcesListStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
        }
    }

    private final class McpResourcesReadStream extends McpRequestStream
    {
        McpResourcesReadStream(
            McpLifecycleStream session,
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long resolvedId,
            long affinity,
            long authorization)
        {
            super(session, sender, originId, routedId, initialId, resolvedId, affinity);
        }

        @Override
        void onAppBeginImpl(
            long traceId,
            long authorization,
            McpBeginExFW mcpBeginEx)
        {
            this.http = new HttpResourcesReadStream(this);
            http.doEncodeRequestBegin(traceId, authorization);
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
        int encodeSlotOffset;
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
            this.originId = mcp.routedId;
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
                onNetEnd(end);
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

            mcp.onNetBegin(begin);
            doNetWindow(traceId, authorization, 0L, 0);
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

            assert replyAck <= replySeq;

            mcp.onNetData(data);
        }

        void doReplyConsumed(
            long traceId,
            long authorization,
            int consumed)
        {
            if (consumed > 0)
            {
                replyAck = Math.min(replyAck + consumed, replySeq);
                assert replyAck <= replySeq;
                doNetWindow(traceId, authorization, 0L, 0);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long sequence = end.sequence();
            final long acknowledge = end.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;
            assert acknowledge <= replyAck;

            replySeq = sequence;
            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            mcp.onNetEnd(end);
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
            final long authorization = abort.authorization();
            final long sequence = abort.sequence();
            final long acknowledge = abort.acknowledge();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence;
            state = McpState.closedReply(state);

            assert replyAck <= replySeq;

            cleanupDecodeSlot();
            mcp.doAppAbort(traceId, authorization);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long authorization = window.authorization();
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
                final long slotTraceId = encodeSlotTraceId;
                final long slotAuthorization = encodeSlotAuthorization;

                encodeSlotOffset = 0;
                encodeNet(slotTraceId, slotAuthorization, encodeBuffer, 0, limit);
            }

            mcp.flushAppWindow(traceId, authorization, 0L, 0);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();
            final long sequence = reset.sequence();
            final long acknowledge = reset.acknowledge();

            assert acknowledge <= sequence;
            assert acknowledge <= initialAck;

            initialAck = acknowledge;
            state = McpState.closedInitial(state);

            assert initialAck <= initialSeq;

            cleanupEncodeSlot();
            mcp.doAppReset(traceId, authorization);
        }

        void onAppReplyWindow(
            long traceId,
            long authorization)
        {
        }

        abstract void doEncodeRequestBegin(long traceId, long authorization);

        void doEncodeRequestData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            doNetData(traceId, authorization, buffer, offset, limit);
        }

        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            doNetEnd(traceId, authorization);
        }

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

        protected void cleanupDecodeSlot()
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
            mcp.doAppAbort(traceId, authorization);
        }
    }

    private final class HttpInitializeRequest extends HttpStream
    {
        HttpInitializeRequest(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
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
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"%s","capabilities":{},\
                "clientInfo":{"name":"%s","version":"%s"}}}\
                """.formatted(MCP_PROTOCOL_VERSION, clientName, clientVersion));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpNotifyInitialized extends HttpStream
    {
        HttpNotifyInitialized(
            McpStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
            long traceId,
            long authorization)
        {
            final String sid = ((McpLifecycleStream) mcp).responseSessionId;
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(extBuffer, 0, extBuffer.capacity())
                .typeId(httpTypeId);
            if (mcp.with != null && mcp.with.headers != null)
            {
                mcp.with.headers.forEach((name, value) ->
                    builder.headersItem(h -> h.name(name).value(value)));
            }
            final HttpBeginExFW httpBeginEx = builder
                .headersItem(h -> h.name(HTTP_HEADER_METHOD).value(HTTP_METHOD_POST))
                .headersItem(h -> h.name(HTTP_HEADER_CONTENT_TYPE).value(CONTENT_TYPE_JSON))
                .headersItem(h -> h.name(HTTP_HEADER_MCP_VERSION).value(MCP_PROTOCOL_VERSION))
                .headersItem(h -> h.name(HTTP_HEADER_SESSION).value(sid))
                .build();

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private abstract class HttpRequestStream extends HttpStream
    {
        protected final McpRequestStream request;

        HttpResponseDecoder decoder;
        JsonParser decodableJson;
        int decodedResultStart;
        boolean netEnded;

        HttpRequestStream(
            McpRequestStream mcp)
        {
            super(mcp);
            this.request = mcp;
            this.decoder = decodeJsonRpc;
            this.decodedResultStart = -1;
        }

        void onNetResponseData(
            DataFW data)
        {
            final long traceId = data.traceId();
            final long authorization = data.authorization();
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

                    if (decodedResultStart < 0)
                    {
                        decoder = decodeJsonRpc;
                        decodeNet(this, buf, 0, decodeSlotOffset);
                    }
                }
            }
            drainResponseToApp(traceId, authorization);
        }

        void flushResponseToApp(
            long traceId,
            long authorization)
        {
            netEnded = true;
            drainResponseToApp(traceId, authorization);
        }

        void drainResponseToApp(
            long traceId,
            long authorization)
        {
            if (decodedResultStart < 0)
            {
                if (netEnded)
                {
                    mcp.doAppEnd(traceId, authorization);
                    cleanupDecodeSlot();
                }
                return;
            }

            final MutableDirectBuffer buf = bufferPool.buffer(decodeSlot);
            final int drainLimit = decodeSlotOffset - 1;

            while (decodedResultStart < drainLimit)
            {
                final int mcpCredit = mcp.replyMax - (int) (mcp.replySeq - mcp.replyAck) - mcp.replyPad;
                if (mcpCredit <= 0)
                {
                    break;
                }
                final int length = Math.min(mcpCredit, drainLimit - decodedResultStart);
                mcp.doAppData(traceId, authorization, buf, decodedResultStart, length);
                decodedResultStart += length;
                doReplyConsumed(traceId, authorization, length);
            }

            if (netEnded && decodedResultStart == drainLimit)
            {
                mcp.doAppEnd(traceId, authorization);
                cleanupDecodeSlot();
            }
            else if (decodedResultStart > 0)
            {
                final int remaining = decodeSlotOffset - decodedResultStart;
                buf.putBytes(0, buf, decodedResultStart, remaining);
                decodeSlotOffset = remaining;
                decodedResultStart = 0;
            }
        }

        @Override
        void onAppReplyWindow(
            long traceId,
            long authorization)
        {
            drainResponseToApp(traceId, authorization);
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
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }
    }

    private final class HttpToolsCallStream extends HttpRequestStream
    {
        HttpToolsCallStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
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
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"prompts/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

    }

    private final class HttpPromptsGetStream extends HttpRequestStream
    {
        HttpPromptsGetStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"prompts/get\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
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
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"resources/list\"}".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

    }

    private final class HttpResourcesReadStream extends HttpRequestStream
    {
        HttpResourcesReadStream(
            McpRequestStream mcp)
        {
            super(mcp);
        }

        @Override
        void doEncodeRequestBegin(
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

            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"resources/read\",\"params\":".formatted(request.requestId));

            doNetBegin(traceId, authorization, httpBeginEx);
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
        }

        @Override
        void doEncodeRequestEnd(
            long traceId,
            long authorization)
        {
            final int codecLength = codecBuffer.putStringWithoutLengthAscii(0, "}");
            doNetData(traceId, authorization, codecBuffer, 0, codecLength);
            doNetEnd(traceId, authorization);
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
                begin.traceId(), begin.authorization(), 0, writeBuffer.capacity(), 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            initialMax = window.maximum();
            if (!endSent && initialMax > 0)
            {
                endSent = true;
                doEnd(net, mcp.originId, mcp.resolvedId, initialId,
                    window.traceId(), window.authorization());
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            final long authorization = end.authorization();
            mcp.doAppEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();
            mcp.doAppAbort(traceId, authorization);
        }
    }

    private final class HttpNotifyCancelled
    {
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final String sessionId;
        private final int requestId;
        private final McpWithConfig with;

        private MessageConsumer net;
        private long authorization;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private int state;

        HttpNotifyCancelled(
            McpRequestStream mcp)
        {
            this.initialId = supplyInitialId.applyAsLong(mcp.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.sessionId = mcp.sessionId;
            this.requestId = mcp.requestId;
            this.originId = mcp.routedId;
            this.routedId = mcp.resolvedId;
            this.affinity = mcp.affinity;
            this.with = mcp.with;
        }

        void doNetBegin(
            long traceId,
            long authorization)
        {
            this.authorization = authorization;
            state = McpState.openingInitial(state);

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

            net = newStream(this::onNetMessage, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization, affinity, httpBeginEx);
        }

        void doNetData(
            long traceId,
            long authorization,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final int length = limit - offset;
            final int reserved = length;

            doData(net, originId, routedId, initialId,
                initialSeq, initialAck, initialMax,
                traceId, authorization,
                DATA_FLAGS_COMPLETE, 0L, reserved,
                buffer, offset, length);

            initialSeq += reserved;
        }

        void doNetEnd(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doEnd(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        void doNetAbort(
            long traceId,
            long authorization)
        {
            if (!McpState.initialClosed(state))
            {
                state = McpState.closedInitial(state);
                doAbort(net, originId, routedId, initialId,
                    initialSeq, initialAck, initialMax,
                    traceId, authorization);
            }
        }

        void doNetReset(
            long traceId,
            long authorization)
        {
            if (!McpState.replyClosed(state))
            {
                state = McpState.closedReply(state);
                doReset(net, originId, routedId, replyId,
                    replySeq, replyAck, replyMax,
                    traceId, authorization);
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
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetAbort(abort);
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
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();

            assert acknowledge <= sequence;

            state = McpState.openedReply(state);
            replySeq = sequence;
            replyAck = acknowledge;
            replyMax = writeBuffer.capacity();

            assert replyAck <= replySeq;

            doWindow(net, originId, routedId, replyId,
                replySeq, replyAck, replyMax,
                traceId, authorization, 0L, 0);
        }

        private void onNetWindow(
            WindowFW window)
        {
            final long traceId = window.traceId();
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();

            assert acknowledge <= sequence;

            state = McpState.openedInitial(state);
            initialAck = acknowledge;
            initialMax = maximum;

            if (!McpState.initialClosed(state))
            {
                final int codecLength = codecBuffer.putStringWithoutLengthAscii(0,
                    """
                    {"jsonrpc":"2.0","method":"notifications/cancelled","params":\
                    {"requestId":%d,"reason":"User cancelled"}}\
                    """.formatted(requestId));

                doNetData(traceId, authorization, codecBuffer, 0, codecLength);
                doNetEnd(traceId, authorization);
            }
        }

        private void onNetEnd(
            EndFW end)
        {
            final long traceId = end.traceId();
            doNetEnd(traceId, authorization);
        }

        private void onNetAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            doNetAbort(traceId, authorization);
        }

        private void onNetReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            doNetReset(traceId, authorization);
        }
    }

    private void decodeNet(
        HttpRequestStream http,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        HttpResponseDecoder previous = null;
        int progress = offset;
        while (progress <= limit && previous != http.decoder)
        {
            previous = http.decoder;
            progress = http.decoder.decode(http, buffer, progress, limit);
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
