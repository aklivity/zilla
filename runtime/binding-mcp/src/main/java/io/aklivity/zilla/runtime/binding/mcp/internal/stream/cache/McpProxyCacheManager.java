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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;

import java.io.Closeable;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.BiConsumer;

import org.agrona.CloseHelper;

import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyCacheHydrater;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public final class McpProxyCacheManager implements McpProxyCacheListener
{
    private static final int KIND_SLOTS = KIND_RESOURCES_TEMPLATES_LIST + 1;
    private static final BiConsumer<String, String> NO_OP = (k, v) -> {};

    private final McpProxyCacheHydrater hydrater;
    private final McpProxyCache cache;
    private final Signaler signaler;
    private final long[] hydrateBackoffMs;
    private final long[] hydrateRetryIds;
    private final Closeable[] watchHandles;

    private McpProxyCacheHandler handler;
    private long refreshId;
    private long reconnectId;
    private long renewId;
    private long sessionBackoffMs;
    private boolean stopped;

    McpProxyCacheManager(
        McpProxyCacheHydrater hydrater,
        McpProxyCache cache,
        Signaler signaler)
    {
        this.hydrater = hydrater;
        this.cache = cache;
        this.signaler = signaler;
        this.hydrateBackoffMs = new long[KIND_SLOTS];
        this.hydrateRetryIds = new long[KIND_SLOTS];
        this.watchHandles = new Closeable[KIND_SLOTS];
        Arrays.fill(this.hydrateRetryIds, NO_CANCEL_ID);
        this.refreshId = NO_CANCEL_ID;
        this.reconnectId = NO_CANCEL_ID;
        this.renewId = NO_CANCEL_ID;
    }

    public void start()
    {
        cache.onReady = this::onCacheReady;
        for (int kind : cache.caches().keySet())
        {
            watchHandles[kind] = cache.caches().get(kind).watch((k, v) -> onStoreChanged(kind));
        }
        handler = hydrater.attach(cache, this);
        handler.start();
    }

    public void stop()
    {
        stopped = true;
        cancelRefresh();
        cancelReconnect();
        cancelLifecycleRenew();
        for (int kind : cache.caches().keySet())
        {
            cancelHydrateRetry(kind);
            closeWatch(kind);
        }
        if (handler != null)
        {
            handler.stop();
            handler = null;
        }
        cache.onReady = null;
    }

    private void onStoreChanged(
        int kind)
    {
        if (stopped)
        {
            return;
        }
        cache.caches().get(kind).get(NO_OP);
    }

    private void closeWatch(
        int kind)
    {
        final Closeable handle = watchHandles[kind];
        if (handle != null)
        {
            watchHandles[kind] = null;
            CloseHelper.quietClose(handle);
        }
    }

    @Override
    public void onOpened()
    {
        if (stopped || handler == null)
        {
            return;
        }
        Arrays.fill(hydrateBackoffMs, 0L);
        sessionBackoffMs = 0L;
        scheduleLifecycleRenew();
        for (int kind : cache.caches().keySet())
        {
            handler.hydrate(kind);
        }
    }

    @Override
    public void onError(
        int kind)
    {
        if (!stopped)
        {
            scheduleHydrateRetry(kind);
        }
    }

    @Override
    public void onChanged(
        int kind)
    {
        if (stopped || handler == null)
        {
            return;
        }
        hydrateBackoffMs[kind] = 0L;
        cancelRefresh();
        handler.hydrate(kind);
    }

    @Override
    public void onClosed()
    {
        if (stopped)
        {
            return;
        }
        cancelRefresh();
        cancelLifecycleRenew();
        for (int kind : cache.caches().keySet())
        {
            cancelHydrateRetry(kind);
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
        scheduleRefresh();
    }

    private void scheduleRefresh()
    {
        // refresh is re-armed both by the local hydrate put-completion and by the store watch that the
        // same put self-triggers; coalesce to a single pending refresh so only one hydrate runs per cycle
        if (cache.cacheTtl == null || refreshId != NO_CANCEL_ID)
        {
            return;
        }
        refreshId = signaler.signalAt(
            Instant.now().plus(cache.cacheTtl), 0, this::onRefreshed);
    }

    private void scheduleLifecycleRenew()
    {
        if (cache.leaseTtl == null)
        {
            return;
        }
        cancelLifecycleRenew();
        renewId = signaler.signalAt(
            Instant.now().plusMillis(cache.renewTtl.toMillis()), 0, this::onLifecycleRenew);
    }

    private void onLifecycleRenew(
        int signalId)
    {
        renewId = NO_CANCEL_ID;
        if (stopped || handler == null)
        {
            return;
        }
        cache.renewLock(renewed ->
        {
            if (stopped)
            {
                return;
            }
            if (renewed)
            {
                scheduleLifecycleRenew();
            }
            else
            {
                // lost ownership of the lifecycle lock — tear down this worker's lifecycle
                // and fall through the existing reconnect path; the holder that took over (or
                // the next race winner after the TTL expiry) will continue refresh work
                if (handler != null)
                {
                    handler.stop();
                    handler = null;
                }
                onClosed();
            }
        });
    }

    private void onRefreshed(
        int signalId)
    {
        refreshId = NO_CANCEL_ID;
        if (stopped || handler == null)
        {
            return;
        }
        for (int kind : cache.caches().keySet())
        {
            handler.hydrate(kind);
        }
    }

    // retries forever, backoff capped at leaseTtl -- a route that never recovers just keeps retrying at
    // that steady-state interval rather than permanently giving up; McpProxyCache.markAttempted (called
    // from McpProxyCacheHydrater after this cycle's failure) already lets checkReady()/register() unblock
    // a new session's initialize without needing hydration to ever succeed or stop trying
    private void scheduleHydrateRetry(
        int kind)
    {
        cancelHydrateRetry(kind);
        long delay = hydrateBackoffMs[kind];
        delay = delay == 0L ? cache.leaseRetry.toMillis() : Math.min(delay * 2L, cache.leaseTtl.toMillis());
        hydrateBackoffMs[kind] = delay;
        hydrateRetryIds[kind] = signaler.signalAt(
            Instant.now().plusMillis(delay), kind, this::onHydrateRetry);
    }

    private void onHydrateRetry(
        int kind)
    {
        hydrateRetryIds[kind] = NO_CANCEL_ID;
        if (stopped || handler == null)
        {
            return;
        }
        handler.hydrate(kind);
    }

    private void scheduleReconnect()
    {
        cancelReconnect();
        long delay = sessionBackoffMs;
        delay = delay == 0L ? cache.leaseRetry.toMillis() : Math.min(delay * 2L, cache.leaseTtl.toMillis());
        sessionBackoffMs = delay;
        reconnectId = signaler.signalAt(
            Instant.now().plusMillis(delay), 0, this::onReconnected);
    }

    private void onReconnected(
        int signalId)
    {
        reconnectId = NO_CANCEL_ID;
        if (stopped)
        {
            return;
        }
        handler = hydrater.attach(cache, this);
        handler.start();
    }

    private void cancelLifecycleRenew()
    {
        if (renewId != NO_CANCEL_ID)
        {
            signaler.cancel(renewId);
            renewId = NO_CANCEL_ID;
        }
    }

    private void cancelRefresh()
    {
        if (refreshId != NO_CANCEL_ID)
        {
            signaler.cancel(refreshId);
            refreshId = NO_CANCEL_ID;
        }
    }

    private void cancelReconnect()
    {
        if (reconnectId != NO_CANCEL_ID)
        {
            signaler.cancel(reconnectId);
            reconnectId = NO_CANCEL_ID;
        }
    }

    private void cancelHydrateRetry(
        int kind)
    {
        if (hydrateRetryIds[kind] != NO_CANCEL_ID)
        {
            signaler.cancel(hydrateRetryIds[kind]);
            hydrateRetryIds[kind] = NO_CANCEL_ID;
        }
    }

    public static final class Factory
    {
        private final McpProxyCacheHydrater hydrater;
        private final Signaler signaler;

        public Factory(
            McpProxyCacheHydrater hydrater,
            Signaler signaler)
        {
            this.hydrater = hydrater;
            this.signaler = signaler;
        }

        public McpProxyCacheManager create(
            McpProxyCache cache)
        {
            return new McpProxyCacheManager(hydrater, cache, signaler);
        }
    }
}
