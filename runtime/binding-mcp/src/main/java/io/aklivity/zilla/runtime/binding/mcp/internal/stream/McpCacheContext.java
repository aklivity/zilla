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

import static io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyCacheHydrater.SIGNAL_INITIATE_LIFECYCLE;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpProxyCacheHydrater.McpHydrateLifecycleStream;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpCacheContext
{
    private static final String STORE_KEY_TOOLS = "tools";
    private static final String STORE_KEY_RESOURCES = "resources";
    private static final String STORE_KEY_PROMPTS = "prompts";
    private static final String STORE_LOCK_SUFFIX = ".lock";
    private static final String STORE_LOCK_VALUE = "1";
    private static final String STORE_LOCK_KEY_TOOLS = STORE_KEY_TOOLS + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_RESOURCES = STORE_KEY_RESOURCES + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_PROMPTS = STORE_KEY_PROMPTS + STORE_LOCK_SUFFIX;
    private static final String STORE_LIFECYCLE_LOCK_KEY = "lifecycle.lock";
    private static final long STORE_TTL_FOREVER = Long.MAX_VALUE;

    public final long bindingId;
    public final GuardHandler guard;
    public final String credentials;
    public final Duration leaseTtl;
    public final Duration leaseRetry;
    public final Duration cacheTtl;

    public String sessionId;
    public long authorization;

    private final StoreHandler store;
    private final Signaler signaler;
    private final McpProxyCacheHydrater hydrater;
    private final McpListCache tools;
    private final McpListCache resources;
    private final McpListCache prompts;
    private final List<McpSignalHandle> awaiters;

    private McpHydrateLifecycleStream lifecycleStream;
    private boolean detached;
    private boolean complete;
    private int populated;
    private int expected;

    public McpCacheContext(
        long bindingId,
        StoreHandler store,
        Signaler signaler,
        McpProxyCacheHydrater hydrater,
        GuardHandler guard,
        String credentials,
        Duration leaseTtl,
        Duration leaseRetry,
        Duration cacheTtl)
    {
        this.bindingId = bindingId;
        this.store = store;
        this.signaler = signaler;
        this.hydrater = hydrater;
        this.guard = guard;
        this.credentials = credentials;
        this.leaseTtl = leaseTtl;
        this.leaseRetry = leaseRetry;
        this.cacheTtl = cacheTtl;
        this.tools = new McpListCache(STORE_KEY_TOOLS, STORE_LOCK_KEY_TOOLS);
        this.resources = new McpListCache(STORE_KEY_RESOURCES, STORE_LOCK_KEY_RESOURCES);
        this.prompts = new McpListCache(STORE_KEY_PROMPTS, STORE_LOCK_KEY_PROMPTS);
        this.awaiters = new ArrayList<>();
    }

    public McpListCache tools()
    {
        return tools;
    }

    public McpListCache resources()
    {
        return resources;
    }

    public McpListCache prompts()
    {
        return prompts;
    }

    public void acquireLifecycle(
        Consumer<Boolean> completion)
    {
        store.putIfAbsent(STORE_LIFECYCLE_LOCK_KEY, STORE_LOCK_VALUE, leaseTtl.toMillis(),
            prior -> completion.accept(prior == null));
    }

    public void releaseLifecycle(
        Consumer<String> completion)
    {
        store.delete(STORE_LIFECYCLE_LOCK_KEY, completion);
    }

    void start()
    {
        detached = false;
        complete = false;
        populated = 0;
        expected = hydrater.activeHydraterCount();
        awaiters.clear();
        signaler.signalAt(Instant.now(), SIGNAL_INITIATE_LIFECYCLE, this::beginLifecycle);
    }

    void detach()
    {
        detached = true;
        if (lifecycleStream != null)
        {
            lifecycleStream.doLifecycleEnd(hydrater.supplyTraceId());
            lifecycleStream = null;
        }
        releaseLifecycle(k -> {});
        awaiters.clear();
    }

    boolean detached()
    {
        return detached;
    }

    void register(
        McpSignalHandle handle)
    {
        if (complete)
        {
            handle.signalVia(signaler);
        }
        else
        {
            awaiters.add(handle);
        }
    }

    void markReady()
    {
        if (!complete)
        {
            populated++;
            if (populated >= expected)
            {
                markComplete();
            }
        }
    }

    void markComplete()
    {
        complete = true;
        for (McpSignalHandle h : awaiters)
        {
            h.signalVia(signaler);
        }
        awaiters.clear();
        releaseLifecycle(k -> {});
    }

    void scheduleRefresh(
        int signalId)
    {
        if (cacheTtl != null)
        {
            signaler.signalAt(Instant.now().plus(cacheTtl), signalId, this::onRefresh);
        }
    }

    void onLifecycleOpened(
        long traceId)
    {
        if (hydrater.activeHydraterCount() == 0)
        {
            markComplete();
        }
        else
        {
            hydrater.initiateListHydraters(this, traceId);
        }
    }

    void onLifecycleClosed()
    {
        lifecycleStream = null;
        releaseLifecycle(k -> {});
    }

    private void beginLifecycle(
        int signalId)
    {
        if (detached)
        {
            return;
        }

        final long traceId = hydrater.supplyTraceId();
        sessionId = hydrater.supplySessionId();
        authorization = guard != null
            ? guard.reauthorize(traceId, bindingId, 0L, credentials)
            : 0L;
        acquireLifecycle(acquired -> onAcquireLifecycleComplete(traceId, acquired));
    }

    private void onAcquireLifecycleComplete(
        long traceId,
        boolean acquired)
    {
        if (detached)
        {
            return;
        }

        if (acquired)
        {
            lifecycleStream = hydrater.newLifecycleStream(this);
            lifecycleStream.doLifecycleBegin(traceId);
        }
        else
        {
            signaler.signalAt(Instant.now().plus(leaseRetry), SIGNAL_INITIATE_LIFECYCLE, this::beginLifecycle);
        }
    }

    private void onRefresh(
        int signalId)
    {
        if (!detached)
        {
            hydrater.refresh(this, signalId);
        }
    }

    public final class McpListCache
    {
        private final String storeKey;
        private final String storeLockKey;

        private McpListCache(
            String storeKey,
            String storeLockKey)
        {
            this.storeKey = storeKey;
            this.storeLockKey = storeLockKey;
        }

        public void get(
            BiConsumer<String, String> completion)
        {
            store.get(storeKey, completion);
        }

        public void put(
            String value,
            Consumer<String> completion)
        {
            store.put(storeKey, value, STORE_TTL_FOREVER, completion);
        }

        public void acquire(
            Consumer<Boolean> completion)
        {
            store.putIfAbsent(storeLockKey, STORE_LOCK_VALUE, leaseTtl.toMillis(),
                prior -> completion.accept(prior == null));
        }

        public void release(
            Consumer<String> completion)
        {
            store.delete(storeLockKey, completion);
        }
    }
}
