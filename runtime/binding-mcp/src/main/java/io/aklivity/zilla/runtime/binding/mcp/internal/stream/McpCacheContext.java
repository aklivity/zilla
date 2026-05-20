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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

    Runnable lifecycleCleanup;

    private final StoreHandler store;
    private final McpListCache tools;
    private final McpListCache resources;
    private final McpListCache prompts;
    private final List<McpSignalHandle> awaiters;

    private McpProxyCacheHydrater hydrater;
    private boolean detached;
    private boolean complete;
    private int populated;
    private int expected;

    public McpCacheContext(
        long bindingId,
        StoreHandler store,
        GuardHandler guard,
        String credentials,
        Duration leaseTtl,
        Duration leaseRetry,
        Duration cacheTtl)
    {
        this.bindingId = bindingId;
        this.store = store;
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

    void bind(
        McpProxyCacheHydrater hydrater,
        int expected)
    {
        this.hydrater = hydrater;
        this.detached = false;
        this.complete = false;
        this.populated = 0;
        this.expected = expected;
        this.awaiters.clear();
    }

    void detach()
    {
        detached = true;
        if (lifecycleCleanup != null)
        {
            lifecycleCleanup.run();
            lifecycleCleanup = null;
        }
        releaseLifecycle(k -> {});
        awaiters.clear();
    }

    boolean detached()
    {
        return detached;
    }

    void onInitiateLifecycle(
        int signalId)
    {
        if (!detached)
        {
            hydrater.beginLifecycle(this);
        }
    }

    void onRefresh(
        int signalId)
    {
        if (!detached)
        {
            hydrater.refresh(this, signalId);
        }
    }

    void register(
        McpSignalHandle handle)
    {
        if (complete)
        {
            handle.signalVia(hydrater.signaler());
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
            h.signalVia(hydrater.signaler());
        }
        awaiters.clear();
        releaseLifecycle(k -> {});
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
