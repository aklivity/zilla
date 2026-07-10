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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_PROMPTS_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_RESOURCES_LIST_CHANGED;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpFlushExFW.KIND_TOOLS_LIST_CHANGED;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;

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
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;

public final class McpProxyCacheHydrater
{
    private static final String NAME = "name";
    private static final String SECURITY_SCHEMES = "securitySchemes";
    private static final String TYPE = "type";
    private static final String SCOPES = "scopes";
    private static final String OAUTH2 = "oauth2";
    private static final String NOAUTH = "noauth";

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
                    cache.resetRouteAuthorizations();
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
            DirectBufferEx buffer,
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
                onChangedIfCached(KIND_TOOLS_LIST);
                break;
            case KIND_PROMPTS_LIST_CHANGED:
                onChangedIfCached(KIND_PROMPTS_LIST);
                break;
            case KIND_RESOURCES_LIST_CHANGED:
                onChangedIfCached(KIND_RESOURCES_LIST);
                onChangedIfCached(KIND_RESOURCES_TEMPLATES_LIST);
                break;
            default:
                break;
            }
        }

        private void onChangedIfCached(
            int kind)
        {
            if (cache.cacheOf(kind) != null)
            {
                onChanged(kind);
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
            final McpBindingConfig binding = supplyBinding.apply(handler.cache.bindingId);
            final McpProxyListFactory listFactory = listFactories.get(kind);
            final List<McpRoutePrefix> routes = binding.resolveAll(traceId, kind);

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
                    final long authorization = binding.routeCacheAuthorization(traceId, route.resolvedId());
                    final List<String> routeScopes = flattenRoles(route.route().roles);
                    final McpListRouteSink sink = new McpListRouteSink(this, route.prefix().asString(), routeScopes);
                    sinks.add(sink);
                    sink.server = listFactory.newHydrationList(
                        handler.lifecycle, sink::onMessage, authorization,
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

    private static List<String> flattenRoles(
        Map<String, List<String>> roles)
    {
        final List<String> result = new ArrayList<>();
        for (List<String> scopes : roles.values())
        {
            for (String scope : scopes)
            {
                if (!result.contains(scope))
                {
                    result.add(scope);
                }
            }
        }
        return result;
    }

    private final class McpListRouteSink
    {
        private final McpListKindHydrater kind;
        private final String prefix;
        private final List<String> routeScopes;
        private final ExpandableArrayBufferEx bodyBuffer;

        private MessageConsumer server;
        private int bodyLen;
        private boolean failed;
        private boolean settled;

        private McpListRouteSink(
            McpListKindHydrater kind,
            String prefix,
            List<String> routeScopes)
        {
            this.kind = kind;
            this.prefix = prefix;
            this.routeScopes = routeScopes;
            this.bodyBuffer = new ExpandableArrayBufferEx();
        }

        private void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
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
            final String items = open >= 0 && close > open ? envelope.substring(open + 1, close) : "";
            return items.isEmpty() ? items : normalizeItems(items, routeScopes);
        }
    }

    // McpScopeFilter's streaming admission check requires each item's "name" key to arrive first
    // (see its class-level contract); south bindings are free to order their own fields however they
    // like -- e.g. many resources put "uri" before "name" -- so every cached item is reordered here,
    // regardless of whether this route also has its own scopes to merge in via injectToolScopes
    private static String normalizeItems(
        String items,
        List<String> routeScopes)
    {
        String result;
        try (JsonReader reader = Json.createReader(new StringReader("[" + items + "]")))
        {
            final JsonArrayBuilder itemsBuilder = Json.createArrayBuilder();
            for (JsonValue item : reader.readArray())
            {
                final JsonObject reordered = nameFirst((JsonObject) item);
                itemsBuilder.add(routeScopes.isEmpty() ? reordered : injectToolScopes(reordered, routeScopes));
            }
            final StringWriter writer = new StringWriter();
            try (JsonWriter jsonWriter = Json.createWriter(writer))
            {
                jsonWriter.writeArray(itemsBuilder.build());
            }
            final String written = writer.toString();
            result = written.substring(1, written.length() - 1);
        }
        catch (JsonException ex)
        {
            result = items;
        }
        return result;
    }

    private static JsonObject nameFirst(
        JsonObject item)
    {
        final JsonValue name = item.get(NAME);
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        if (name != null)
        {
            builder.add(NAME, name);
        }
        for (Map.Entry<String, JsonValue> entry : item.entrySet())
        {
            if (!NAME.equals(entry.getKey()))
            {
                builder.add(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private static JsonObject injectToolScopes(
        JsonObject tool,
        List<String> routeScopes)
    {
        final JsonArray schemes = tool.getJsonArray(SECURITY_SCHEMES);

        final JsonObject result;
        if (schemes == null || schemes.isEmpty())
        {
            result = Json.createObjectBuilder(tool)
                .add(SECURITY_SCHEMES, Json.createArrayBuilder()
                    .add(newOAuth2Scheme(routeScopes)))
                .build();
        }
        else if (NOAUTH.equals(schemes.getJsonObject(0).getString(TYPE, null)))
        {
            result = tool;
        }
        else
        {
            final JsonArrayBuilder schemesBuilder = Json.createArrayBuilder()
                .add(mergeScopes(schemes.getJsonObject(0), routeScopes));
            for (int i = 1; i < schemes.size(); i++)
            {
                schemesBuilder.add(schemes.get(i));
            }
            result = Json.createObjectBuilder(tool)
                .add(SECURITY_SCHEMES, schemesBuilder)
                .build();
        }
        return result;
    }

    private static JsonObject mergeScopes(
        JsonObject scheme,
        List<String> routeScopes)
    {
        final List<String> merged = new ArrayList<>();
        final JsonArray scopes = scheme.getJsonArray(SCOPES);
        if (scopes != null)
        {
            for (JsonString scope : scopes.getValuesAs(JsonString.class))
            {
                merged.add(scope.getString());
            }
        }
        for (String scope : routeScopes)
        {
            if (!merged.contains(scope))
            {
                merged.add(scope);
            }
        }

        final JsonArrayBuilder scopesBuilder = Json.createArrayBuilder();
        merged.forEach(scopesBuilder::add);

        return Json.createObjectBuilder(scheme)
            .add(SCOPES, scopesBuilder)
            .build();
    }

    private static JsonObject newOAuth2Scheme(
        List<String> scopes)
    {
        final JsonArrayBuilder scopesBuilder = Json.createArrayBuilder();
        scopes.forEach(scopesBuilder::add);
        return Json.createObjectBuilder()
            .add(TYPE, OAUTH2)
            .add(SCOPES, scopesBuilder)
            .build();
    }
}
