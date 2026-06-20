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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_PROMPTS_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_RESOURCES_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_TOOLS_LIST_CHANGED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpRoutePrefix;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyLifecycleFactory.McpLifecycleServer;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCacheHandler;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCacheListener;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class McpProxyCacheHydrater
{
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();
    private final McpFlushExFW mcpFlushExRO = new McpFlushExFW();

    private final BufferPool bufferPool;
    private final LongSupplier supplyTraceId;
    private final LongFunction<McpBindingConfig> supplyBinding;
    private final McpProxyLifecycleFactory lifecycleFactory;
    private final Int2ObjectHashMap<McpProxyListFactory> listFactories;

    public McpProxyCacheHydrater(
        BufferPool bufferPool,
        LongSupplier supplyTraceId,
        LongFunction<McpBindingConfig> supplyBinding,
        McpProxyLifecycleFactory lifecycleFactory,
        Int2ObjectHashMap<McpProxyListFactory> listFactories)
    {
        this.bufferPool = bufferPool;
        this.supplyTraceId = supplyTraceId;
        this.supplyBinding = supplyBinding;
        this.lifecycleFactory = lifecycleFactory;
        this.listFactories = listFactories;
    }

    public McpProxyCacheHandler attach(
        McpProxyCache cache,
        McpProxyCacheListener listener)
    {
        return new HandlerImpl(cache, listener);
    }

    private final class HandlerImpl implements McpProxyCacheHandler
    {
        private final McpProxyCache cache;
        private final McpProxyCacheListener listener;

        private McpLifecycleServer lifecycle;
        private boolean lifecycleOpened;
        private boolean stopped;
        private boolean closedNotified;

        HandlerImpl(
            McpProxyCache cache,
            McpProxyCacheListener listener)
        {
            this.cache = cache;
            this.listener = listener;
        }

        @Override
        public void start()
        {
            if (!stopped)
            {
                cache.acquireLock(this::onAcquireLifecycleComplete);
            }
        }

        @Override
        public void stop()
        {
            stopped = true;
            if (lifecycle != null)
            {
                final long traceId = supplyTraceId.getAsLong();
                lifecycle.driveHydrationAbort(traceId);
                lifecycle = null;
            }
            cache.releaseLock(k -> {});
        }

        @Override
        public void hydrate(
            int kind)
        {
            if (!stopped && lifecycle != null && lifecycleOpened)
            {
                new McpListKindHydrater(this, kind).start();
            }
        }

        @Override
        public void onChanged(
            int kind)
        {
            if (!stopped)
            {
                listener.onChanged(kind);
            }
        }

        private void onAcquireLifecycleComplete(
            boolean acquired)
        {
            if (!stopped)
            {
                if (acquired)
                {
                    final long traceId = supplyTraceId.getAsLong();
                    cache.authorization = cache.guard != null
                        ? cache.guard.reauthorize(traceId, cache.bindingId, 0L, cache.credentials)
                        : 0L;
                    lifecycle = lifecycleFactory.newHydrationLifecycle(
                        supplyBinding.apply(cache.bindingId), this::onLifecycleMessage,
                        cache.bindingId, cache.authorization);
                    lifecycle.driveHydrationBegin(traceId);
                }
                else
                {
                    notifyClosed();
                }
            }
        }

        private void onLifecycleMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                onLifecycleBegin(beginRO.wrap(buffer, index, index + length));
                break;
            case FlushFW.TYPE_ID:
                onLifecycleFlush(flushRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                endRO.wrap(buffer, index, index + length);
                onLifecycleClosed();
                break;
            case AbortFW.TYPE_ID:
                abortRO.wrap(buffer, index, index + length);
                onLifecycleClosed();
                break;
            case ResetFW.TYPE_ID:
                resetRO.wrap(buffer, index, index + length);
                onLifecycleClosed();
                break;
            default:
                break;
            }
        }

        private void onLifecycleBegin(
            BeginFW begin)
        {
            final OctetsFW extension = begin.extension();
            final McpBeginExFW beginEx = extension.sizeof() > 0
                ? mcpBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit())
                : null;
            if (beginEx != null && beginEx.kind() == McpBeginExFW.KIND_LIFECYCLE)
            {
                cache.sessionId = beginEx.lifecycle().sessionId().asString();
            }

            if (!stopped && !lifecycleOpened)
            {
                lifecycleOpened = true;
                listener.onOpened();
            }
        }

        private void onLifecycleFlush(
            FlushFW flush)
        {
            final OctetsFW extension = flush.extension();
            final McpFlushExFW flushEx = mcpFlushExRO.wrap(extension.buffer(), extension.offset(), extension.limit());

            switch (flushEx.kind())
            {
            case KIND_TOOLS_LIST_CHANGED:
                onChanged(KIND_TOOLS_LIST);
                break;
            case KIND_PROMPTS_LIST_CHANGED:
                onChanged(KIND_PROMPTS_LIST);
                break;
            case KIND_RESOURCES_LIST_CHANGED:
                onChanged(KIND_RESOURCES_LIST);
                break;
            default:
                break;
            }
        }

        private void onLifecycleClosed()
        {
            lifecycle = null;
            cache.releaseLock(k -> {});
            notifyClosed();
        }

        private void notifyClosed()
        {
            if (!stopped && !closedNotified)
            {
                closedNotified = true;
                listener.onClosed();
            }
        }
    }

    private final class McpListKindHydrater
    {
        private final HandlerImpl handler;
        private final int kind;
        private final List<String> prefixes;
        private final List<McpListRouteSink> sinks;

        private int pending;
        private boolean failedAny;

        private McpListKindHydrater(
            HandlerImpl handler,
            int kind)
        {
            this.handler = handler;
            this.kind = kind;
            this.prefixes = new ArrayList<>();
            this.sinks = new ArrayList<>();
        }

        private void start()
        {
            final McpProxyCache.McpListCache listCache = handler.cache.cacheOf(kind);
            if (handler.cache.populated)
            {
                listCache.acquire(this::onAcquireComplete);
            }
            else
            {
                listCache.get((k, v) -> onGetComplete(v));
            }
        }

        private void onGetComplete(
            String value)
        {
            if (!handler.stopped && handler.lifecycle != null && value == null)
            {
                handler.cache.cacheOf(kind).acquire(this::onAcquireComplete);
            }
        }

        private void onAcquireComplete(
            boolean acquired)
        {
            if (!handler.stopped && handler.lifecycle != null)
            {
                if (acquired)
                {
                    startRoutes();
                }
                else
                {
                    handler.listener.onError(kind);
                }
            }
        }

        private void startRoutes()
        {
            final long traceId = supplyTraceId.getAsLong();
            final McpProxyListFactory listFactory = listFactories.get(kind);
            final List<McpRoutePrefix> routes =
                supplyBinding.apply(handler.cache.bindingId).resolveAll(kind, handler.cache.authorization);

            for (McpRoutePrefix route : routes)
            {
                prefixes.add(route.prefix().asString());
            }

            if (routes.isEmpty())
            {
                assemble(traceId);
            }
            else
            {
                pending = routes.size();
                for (McpRoutePrefix route : routes)
                {
                    final McpListRouteSink sink = new McpListRouteSink(this, route.prefix().asString());
                    sinks.add(sink);
                    sink.server = listFactory.newHydrationList(
                        handler.lifecycle, sink::onMessage, handler.cache.authorization,
                        bufferPool.slotCapacity(), route, traceId);
                }
            }
        }

        private void onRouteSettled(
            McpListRouteSink sink,
            long traceId)
        {
            pending--;
            if (sink.failed)
            {
                failedAny = true;
            }
            else
            {
                handler.cache.cacheOf(kind).putFragment(sink.prefix, sink.fragment());
            }

            if (pending == 0)
            {
                assemble(traceId);
            }
        }

        private void assemble(
            long traceId)
        {
            final McpProxyCache.McpListCache listCache = handler.cache.cacheOf(kind);
            if (listCache.fragmentsAbsent(prefixes))
            {
                terminal(true);
            }
            else
            {
                final McpProxyListFactory listFactory = listFactories.get(kind);
                listCache.putAssembled(listFactory.hydrationPrelude(), listFactory.hydrationClose(),
                    prefixes, k -> terminal(failedAny));
            }
        }

        private void terminal(
            boolean failed)
        {
            handler.cache.cacheOf(kind).release(k -> {});
            if (failed && !handler.stopped)
            {
                handler.listener.onError(kind);
            }
        }
    }

    private final class McpListRouteSink
    {
        private final McpListKindHydrater kind;
        private final String prefix;
        private final ExpandableArrayBuffer bodyBuffer;

        private MessageConsumer server;
        private int bodyLen;
        private boolean failed;
        private boolean settled;

        private McpListRouteSink(
            McpListKindHydrater kind,
            String prefix)
        {
            this.kind = kind;
            this.prefix = prefix;
            this.bodyBuffer = new ExpandableArrayBuffer();
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
                beginRO.wrap(buffer, index, index + length);
                break;
            case DataFW.TYPE_ID:
                onData(dataRO.wrap(buffer, index, index + length));
                break;
            case EndFW.TYPE_ID:
                onEnd(endRO.wrap(buffer, index, index + length));
                break;
            case AbortFW.TYPE_ID:
                onAbort(abortRO.wrap(buffer, index, index + length));
                break;
            default:
                break;
            }
        }

        private void onData(
            DataFW data)
        {
            final OctetsFW payload = data.payload();
            if (payload != null)
            {
                final int payloadLen = payload.sizeof();
                bodyBuffer.putBytes(bodyLen, payload.buffer(), payload.offset(), payloadLen);
                bodyLen += payloadLen;
            }
        }

        private void onEnd(
            EndFW end)
        {
            settle(end.traceId(), false);
        }

        private void onAbort(
            AbortFW abort)
        {
            settle(abort.traceId(), true);
        }

        private void settle(
            long traceId,
            boolean failed)
        {
            if (!settled)
            {
                settled = true;
                this.failed = failed;
                kind.onRouteSettled(this, traceId);
            }
        }

        private String fragment()
        {
            final String envelope = bodyBuffer.getStringWithoutLengthUtf8(0, bodyLen);
            final int open = envelope.indexOf('[');
            final int close = envelope.lastIndexOf(']');
            return open >= 0 && close > open ? envelope.substring(open + 1, close) : "";
        }
    }
}
