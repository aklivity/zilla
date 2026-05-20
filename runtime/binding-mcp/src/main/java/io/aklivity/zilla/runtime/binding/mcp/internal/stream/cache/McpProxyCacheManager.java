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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class McpProxyCacheManager implements McpProxyCacheListener
{
    private static final int KIND_SLOTS = KIND_RESOURCES_LIST + 1;

    private final McpProxyCacheHydrater hydrater;
    private final McpProxyCache cache;
    private final Signaler signaler;
    private final int[] activeKinds;
    private final long[] hydrateBackoffMs;
    private final long[] hydrateCancelIds;

    private McpProxyCacheHandler handler;
    private long refreshCancelId;
    private long reconnectCancelId;
    private long sessionBackoffMs;
    private boolean stopped;

    McpProxyCacheManager(
        McpProxyCacheHydrater hydrater,
        McpProxyCache cache)
    {
        this.hydrater = hydrater;
        this.cache = cache;
        this.signaler = cache.signaler;
        this.hydrateBackoffMs = new long[KIND_SLOTS];
        this.hydrateCancelIds = new long[KIND_SLOTS];
        Arrays.fill(this.hydrateCancelIds, NO_CANCEL_ID);
        this.refreshCancelId = NO_CANCEL_ID;
        this.reconnectCancelId = NO_CANCEL_ID;

        final List<Integer> kinds = new ArrayList<>();
        if (cache.hydrateFilter.test(KIND_TOOLS_LIST))
        {
            kinds.add(KIND_TOOLS_LIST);
        }
        if (cache.hydrateFilter.test(KIND_RESOURCES_LIST))
        {
            kinds.add(KIND_RESOURCES_LIST);
        }
        if (cache.hydrateFilter.test(KIND_PROMPTS_LIST))
        {
            kinds.add(KIND_PROMPTS_LIST);
        }
        this.activeKinds = kinds.stream().mapToInt(Integer::intValue).toArray();
    }

    public void start()
    {
        cache.onReady = this::onCacheReady;
        handler = hydrater.attach(cache, this);
        handler.start();
    }

    public void stop()
    {
        stopped = true;
        cancelRefresh();
        cancelReconnect();
        for (int kind : activeKinds)
        {
            cancelHydrate(kind);
        }
        if (handler != null)
        {
            handler.stop();
            handler = null;
        }
        cache.onReady = null;
    }

    @Override
    public void onError(
        int kind)
    {
        if (!stopped)
        {
            scheduleHydrate(kind);
        }
    }

    @Override
    public void onClosed()
    {
        if (stopped)
        {
            return;
        }
        cancelRefresh();
        for (int kind : activeKinds)
        {
            cancelHydrate(kind);
            cache.onPurged(kind);
        }
        handler = null;
        scheduleReconnect();
    }

    private void onCacheReady()
    {
        if (stopped)
        {
            return;
        }
        cache.releaseLifecycle(k -> {});
        Arrays.fill(hydrateBackoffMs, 0L);
        sessionBackoffMs = 0L;
        scheduleRefresh();
    }

    private void scheduleRefresh()
    {
        if (cache.cacheTtl == null)
        {
            return;
        }
        cancelRefresh();
        refreshCancelId = signaler.signalAt(
            Instant.now().plus(cache.cacheTtl), 0, this::onRefreshFire);
    }

    private void onRefreshFire(
        int signalId)
    {
        refreshCancelId = NO_CANCEL_ID;
        if (stopped || handler == null)
        {
            return;
        }
        for (int kind : activeKinds)
        {
            handler.hydrate(kind);
        }
    }

    private void scheduleHydrate(
        int kind)
    {
        cancelHydrate(kind);
        long delay = hydrateBackoffMs[kind];
        delay = delay == 0L ? cache.leaseRetry.toMillis() : Math.min(delay * 2L, cache.leaseTtl.toMillis());
        hydrateBackoffMs[kind] = delay;
        hydrateCancelIds[kind] = signaler.signalAt(
            Instant.now().plusMillis(delay), kind, this::onHydrated);
    }

    private void onHydrated(
        int signalId)
    {
        hydrateCancelIds[signalId] = NO_CANCEL_ID;
        if (stopped || handler == null)
        {
            return;
        }
        handler.hydrate(signalId);
    }

    private void scheduleReconnect()
    {
        cancelReconnect();
        long delay = sessionBackoffMs;
        delay = delay == 0L ? cache.leaseRetry.toMillis() : Math.min(delay * 2L, cache.leaseTtl.toMillis());
        sessionBackoffMs = delay;
        reconnectCancelId = signaler.signalAt(
            Instant.now().plusMillis(delay), 0, this::onReconnectFire);
    }

    private void onReconnectFire(
        int signalId)
    {
        reconnectCancelId = NO_CANCEL_ID;
        if (stopped)
        {
            return;
        }
        handler = hydrater.attach(cache, this);
        handler.start();
    }

    private void cancelRefresh()
    {
        if (refreshCancelId != NO_CANCEL_ID)
        {
            signaler.cancel(refreshCancelId);
            refreshCancelId = NO_CANCEL_ID;
        }
    }

    private void cancelReconnect()
    {
        if (reconnectCancelId != NO_CANCEL_ID)
        {
            signaler.cancel(reconnectCancelId);
            reconnectCancelId = NO_CANCEL_ID;
        }
    }

    private void cancelHydrate(
        int kind)
    {
        if (hydrateCancelIds[kind] != NO_CANCEL_ID)
        {
            signaler.cancel(hydrateCancelIds[kind]);
            hydrateCancelIds[kind] = NO_CANCEL_ID;
        }
    }

    public static final class Factory
    {
        private final McpProxyCacheHydrater hydrater;

        public Factory(
            McpConfiguration config,
            EngineContext context)
        {
            this.hydrater = new McpProxyCacheHydrater(config, context);
        }

        public McpProxyCacheManager create(
            McpProxyCache cache)
        {
            return new McpProxyCacheManager(hydrater, cache);
        }
    }
}
