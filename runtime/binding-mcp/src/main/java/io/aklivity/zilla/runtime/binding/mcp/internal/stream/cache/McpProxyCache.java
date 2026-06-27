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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpProxyCache
{
    private static final String STORE_KEY_TOOLS = "tools";
    private static final String STORE_KEY_RESOURCES = "resources";
    private static final String STORE_KEY_PROMPTS = "prompts";
    private static final String STORE_LOCK_SUFFIX = ".lock";
    private static final String STORE_LOCK_KEY_TOOLS = STORE_KEY_TOOLS + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_RESOURCES = STORE_KEY_RESOURCES + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_PROMPTS = STORE_KEY_PROMPTS + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_LIFECYCLE = "lifecycle.lock";
    private static final Duration STORE_TTL_FOREVER = null;

    public final long bindingId;
    public final GuardHandler guard;
    public final String credentials;
    public final Duration leaseTtl;
    public final Duration renewTtl;
    public final Duration leaseRetry;
    public final int hydrateAttemptsMax;
    public final Duration cacheTtl;

    public String sessionId;
    public long authorization;

    private final StoreHandler store;
    private final Int2ObjectHashMap<McpListCache> caches;
    private final List<Runnable> awaiters;
    private final CRC32 crc32 = new CRC32();
    private String lockToken;

    public boolean populated;

    Runnable onReady;
    public ListChangedListener onSettled = (kind, changed, value) -> {};

    @FunctionalInterface
    public interface ListChangedListener
    {
        void accept(int kind, boolean changed, String value);
    }

    public McpProxyCache(
        BindingConfig binding,
        McpConfiguration config,
        EngineContext context,
        McpCacheConfig cache)
    {
        this.bindingId = binding.id;
        this.store = context.supplyStore(binding.resolveId.applyAsLong(cache.store));
        this.guard = Optional.ofNullable(cache.authorization)
            .map(a -> a.name)
            .map(binding.resolveId::applyAsLong)
            .map(context::supplyGuard)
            .orElse(null);
        this.credentials = Optional.ofNullable(cache.authorization)
            .map(a -> a.credentials)
            .orElse(null);
        this.leaseTtl = config.leaseTtl();
        this.renewTtl = this.leaseTtl.dividedBy(3);
        this.leaseRetry = config.leaseRetry();
        this.hydrateAttemptsMax = config.hydrateAttemptsMax();
        this.cacheTtl = cache.ttl;
        this.awaiters = new ArrayList<>();
        this.caches = new Int2ObjectHashMap<>();

        final IntPredicate filter = config.hydrateFilter();
        if (filter.test(KIND_TOOLS_LIST))
        {
            caches.put(KIND_TOOLS_LIST, new McpListCache(KIND_TOOLS_LIST, STORE_KEY_TOOLS, STORE_LOCK_KEY_TOOLS));
        }
        if (filter.test(KIND_RESOURCES_LIST))
        {
            caches.put(KIND_RESOURCES_LIST,
                new McpListCache(KIND_RESOURCES_LIST, STORE_KEY_RESOURCES, STORE_LOCK_KEY_RESOURCES));
        }
        if (filter.test(KIND_PROMPTS_LIST))
        {
            caches.put(KIND_PROMPTS_LIST, new McpListCache(KIND_PROMPTS_LIST, STORE_KEY_PROMPTS, STORE_LOCK_KEY_PROMPTS));
        }
    }

    public McpListCache cacheOf(
        int kind)
    {
        return caches.get(kind);
    }

    public Int2ObjectHashMap<McpListCache> caches()
    {
        return caches;
    }

    public void register(
        Runnable awaiter)
    {
        if (populated)
        {
            awaiter.run();
        }
        else
        {
            awaiters.add(awaiter);
        }
    }

    public void acquireLock(
        Consumer<Boolean> completion)
    {
        store.lock(STORE_LOCK_KEY_LIFECYCLE, leaseTtl, (k, t) ->
        {
            lockToken = t;
            completion.accept(t != null);
        });
    }

    public void releaseLock(
        Consumer<String> completion)
    {
        final String token = lockToken;
        lockToken = null;
        if (token != null)
        {
            store.unlock(STORE_LOCK_KEY_LIFECYCLE, token, completion);
        }
        else
        {
            completion.accept(null);
        }
    }

    void renewLock(
        Consumer<Boolean> completion)
    {
        final String token = lockToken;
        if (token != null)
        {
            store.renew(STORE_LOCK_KEY_LIFECYCLE, token, leaseTtl, renewed ->
            {
                if (renewed == null)
                {
                    lockToken = null;
                }
                completion.accept(renewed != null);
            });
        }
        else
        {
            completion.accept(false);
        }
    }

    void onPurged(
        int kind)
    {
        final McpListCache cache = caches.get(kind);
        if (cache != null)
        {
            cache.populated = false;
        }
        populated = false;
    }

    public void markDegraded(
        int kind)
    {
        final McpListCache cache = caches.get(kind);
        if (cache != null && !cache.populated)
        {
            cache.degraded = true;
            checkReady();
        }
    }

    private void checkReady()
    {
        for (McpListCache cache : caches.values())
        {
            if (!cache.settled())
            {
                return;
            }
        }
        populated = true;
        if (onReady != null)
        {
            onReady.run();
        }
        for (Runnable awaiter : awaiters)
        {
            awaiter.run();
        }
        awaiters.clear();
    }

    public final class McpListCache
    {
        public final int kind;

        private final String storeKey;
        private final String storeLockKey;
        private final Map<String, String> fragments;
        private Map<String, List<String>> scopesByName;
        private long lastChecksum = -1L;
        private String lockToken;

        boolean populated;
        boolean degraded;

        boolean settled()
        {
            return populated || degraded;
        }

        public boolean degraded()
        {
            return degraded;
        }

        public Map<String, List<String>> scopesByName()
        {
            return scopesByName != null ? scopesByName : Collections.emptyMap();
        }

        private McpListCache(
            int kind,
            String storeKey,
            String storeLockKey)
        {
            this.kind = kind;
            this.storeKey = storeKey;
            this.storeLockKey = storeLockKey;
            this.fragments = new TreeMap<>();
        }

        public void putFragment(
            String prefix,
            String items)
        {
            fragments.put(prefix, items);
        }

        public boolean fragmentsAbsent(
            List<String> orderedPrefixes)
        {
            boolean absent = true;
            for (String prefix : orderedPrefixes)
            {
                if (fragments.containsKey(prefix))
                {
                    absent = false;
                    break;
                }
            }
            return absent;
        }

        public void putAssembled(
            String prelude,
            String close,
            List<String> orderedPrefixes,
            Consumer<String> completion)
        {
            final StringBuilder builder = new StringBuilder(prelude);
            boolean first = true;
            for (String prefix : orderedPrefixes)
            {
                final String items = fragments.get(prefix);
                if (items != null && !items.isEmpty())
                {
                    if (!first)
                    {
                        builder.append(',');
                    }
                    builder.append(items);
                    first = false;
                }
            }
            builder.append(close);
            put(builder.toString(), completion);
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
            crc32.reset();
            crc32.update(value.getBytes(StandardCharsets.UTF_8));
            final long newChecksum = crc32.getValue();
            final boolean changed = lastChecksum != -1L && lastChecksum != newChecksum;
            lastChecksum = newChecksum;
            rebuildScopeIndex(value);
            store.put(storeKey, value, STORE_TTL_FOREVER, completion.andThen(this::checkPut)
                .andThen(k -> onSettled.accept(kind, changed, value)));
        }

        public void acquire(
            Consumer<Boolean> completion)
        {
            store.lock(storeLockKey, leaseTtl, (k, t) ->
            {
                // only adopt the token on success; a failed acquire (t == null) from an overlapping
                // attempt must not clobber a token already held, otherwise release() cannot unlock it
                if (t != null)
                {
                    lockToken = t;
                }
                completion.accept(t != null);
            });
        }

        public void release(
            Consumer<String> completion)
        {
            final String token = lockToken;
            lockToken = null;
            if (token != null)
            {
                store.unlock(storeLockKey, token, completion);
            }
            else
            {
                completion.accept(null);
            }
        }

        public Closeable watch(
            BiConsumer<String, String> listener)
        {
            return store.watch(storeKey, listener);
        }

        private void checkGet(
            String key,
            String value)
        {
            final boolean changed;
            if (value != null)
            {
                crc32.reset();
                crc32.update(value.getBytes(StandardCharsets.UTF_8));
                final long newChecksum = crc32.getValue();
                changed = lastChecksum != -1L && lastChecksum != newChecksum;
                lastChecksum = newChecksum;
                rebuildScopeIndex(value);
            }
            else
            {
                changed = false;
            }
            populated = value != null;
            if (populated)
            {
                degraded = false;
            }
            checkReady();
            onSettled.accept(kind, changed, value);
        }

        private void checkPut(
            String key)
        {
            populated = true;
            degraded = false;
            checkReady();
        }

        private void rebuildScopeIndex(
            String value)
        {
            if (kind != KIND_TOOLS_LIST || value == null)
            {
                scopesByName = null;
                return;
            }

            final Map<String, List<String>> index = new LinkedHashMap<>();

            try (JsonReader reader = Json.createReader(
                new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))))
            {
                final JsonObject root = reader.readObject();
                if (root.containsKey("tools"))
                {
                    final JsonArray tools = root.getJsonArray("tools");
                    for (JsonValue item : tools)
                    {
                        final JsonObject tool = item.asJsonObject();
                        if (tool.containsKey("name"))
                        {
                            final String name = tool.getString("name");
                            final List<String> scopes = extractScopes(tool);
                            if (scopes != null)
                            {
                                index.put(name, scopes);
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                index.clear();
            }

            scopesByName = index.isEmpty() ? null : index;
        }

        private List<String> extractScopes(
            JsonObject tool)
        {
            if (!tool.containsKey("securitySchemes"))
            {
                return null;
            }

            final JsonObject schemes = tool.getJsonObject("securitySchemes");

            for (String schemeName : schemes.keySet())
            {
                final JsonObject scheme = schemes.getJsonObject(schemeName);
                final String type = scheme.getString("type", "");

                if ("noauth".equals(type))
                {
                    return null;
                }

                if (scheme.containsKey("scopes"))
                {
                    final JsonArray scopeArray = scheme.getJsonArray("scopes");
                    return scopeArray.stream()
                        .map(v -> ((JsonString) v).getString())
                        .collect(Collectors.toList());
                }
            }

            return List.of();
        }
    }
}
