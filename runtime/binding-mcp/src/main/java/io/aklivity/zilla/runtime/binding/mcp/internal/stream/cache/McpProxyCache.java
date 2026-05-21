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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpSignalHandle;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpProxyCache
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

    final Signaler signaler;
    final IntPredicate hydrateFilter;

    private final StoreHandler store;
    private final McpListCache tools;
    private final McpListCache resources;
    private final McpListCache prompts;
    private final List<McpListCache> caches;
    private final List<McpSignalHandle> awaiters;

    boolean populated;

    Runnable onReady;

    public McpProxyCache(
        BindingConfig binding,
        McpConfiguration config,
        EngineContext context,
        McpCacheConfig cache)
    {
        this.bindingId = binding.id;
        this.store = context.supplyStore(binding.resolveId.applyAsLong(cache.store));
        this.signaler = context.signaler();
        this.hydrateFilter = config.hydrateFilter();
        this.guard = Optional.ofNullable(cache.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);
        this.credentials = Optional.ofNullable(cache.authorization)
            .map(a -> a.credentials)
            .orElse(null);
        this.leaseTtl = config.leaseTtl();
        this.leaseRetry = config.leaseRetry();
        this.cacheTtl = cache.ttl;
        this.tools = new McpListCache(STORE_KEY_TOOLS, STORE_LOCK_KEY_TOOLS);
        this.resources = new McpListCache(STORE_KEY_RESOURCES, STORE_LOCK_KEY_RESOURCES);
        this.prompts = new McpListCache(STORE_KEY_PROMPTS, STORE_LOCK_KEY_PROMPTS);
        this.awaiters = new ArrayList<>();

        final List<McpListCache> active = new ArrayList<>();
        if (hydrateFilter.test(KIND_TOOLS_LIST))
        {
            active.add(tools);
        }
        if (hydrateFilter.test(KIND_RESOURCES_LIST))
        {
            active.add(resources);
        }
        if (hydrateFilter.test(KIND_PROMPTS_LIST))
        {
            active.add(prompts);
        }
        this.caches = active;
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

    public void register(
        McpSignalHandle handle)
    {
        if (populated)
        {
            handle.signalVia(signaler);
        }
        else
        {
            awaiters.add(handle);
        }
    }

    List<McpListCache> caches()
    {
        return caches;
    }

    void acquireLifecycle(
        Consumer<Boolean> completion)
    {
        store.putIfAbsent(STORE_LIFECYCLE_LOCK_KEY, STORE_LOCK_VALUE, leaseTtl.toMillis(),
            prior -> completion.accept(prior == null));
    }

    void releaseLifecycle(
        Consumer<String> completion)
    {
        store.delete(STORE_LIFECYCLE_LOCK_KEY, completion);
    }

    void onPurged(
        int kind)
    {
        switch (kind)
        {
        case KIND_TOOLS_LIST:
            tools.populated = false;
            break;
        case KIND_RESOURCES_LIST:
            resources.populated = false;
            break;
        case KIND_PROMPTS_LIST:
            prompts.populated = false;
            break;
        default:
            break;
        }
        populated = false;
    }

    private void checkReady()
    {
        for (McpListCache cache : caches)
        {
            if (!cache.populated)
            {
                return;
            }
        }
        populated = true;
        if (onReady != null)
        {
            onReady.run();
        }
        for (McpSignalHandle h : awaiters)
        {
            h.signalVia(signaler);
        }
        awaiters.clear();
    }

    public final class McpListCache
    {
        private final String storeKey;
        private final String storeLockKey;

        boolean populated;

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
            store.get(storeKey, completion.andThen(this::checkGet));
        }

        public void put(
            String value,
            Consumer<String> completion)
        {
            store.put(storeKey, value, STORE_TTL_FOREVER, completion.andThen(this::checkPut));
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

        private void checkGet(
            String key,
            String value)
        {
            populated = value != null;
            checkReady();
        }

        private void checkPut(
            String key)
        {
            populated = true;
            checkReady();
        }
    }
}
