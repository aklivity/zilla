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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.zip.CRC32;

import jakarta.json.stream.JsonParser;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpSearchToolDescriptor;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolSearchDocumentScanner;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolSearchIndexFactory;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpProxyCache
{
    private static final String STORE_KEY_TOOLS = "tools";
    private static final String STORE_KEY_RESOURCES = "resources";
    private static final String STORE_KEY_RESOURCES_TEMPLATES = "resources/templates";
    private static final String STORE_KEY_PROMPTS = "prompts";
    private static final String STORE_LOCK_SUFFIX = ".lock";
    private static final String STORE_LOCK_KEY_TOOLS = STORE_KEY_TOOLS + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_RESOURCES = STORE_KEY_RESOURCES + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_RESOURCES_TEMPLATES = STORE_KEY_RESOURCES_TEMPLATES + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_PROMPTS = STORE_KEY_PROMPTS + STORE_LOCK_SUFFIX;
    private static final String STORE_LOCK_KEY_LIFECYCLE = "lifecycle.lock";
    private static final Duration STORE_TTL_FOREVER = null;

    private static final List<String> EMPTY_ROLES = List.of();
    // identity sentinel distinct from EMPTY_ROLES, signalling a scheme that requires no authorization
    private static final List<String> NO_AUTH = new ArrayList<>(0);

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
            final McpToolSearchIndex searchIndex = cache.tools != null
                ? new McpToolSearchIndexFactory().create(cache.tools.search)
                : null;
            final List<String> searchFields = cache.tools != null && cache.tools.search != null
                ? cache.tools.search.fields
                : null;
            final byte[] searchToolBytes = cache.tools != null && cache.tools.search != null
                ? McpSearchToolDescriptor.build(cache.tools.search.tool)
                : null;
            caches.put(KIND_TOOLS_LIST,
                new McpListCache(KIND_TOOLS_LIST, STORE_KEY_TOOLS, STORE_LOCK_KEY_TOOLS,
                    searchIndex, searchFields, searchToolBytes));
        }
        if (filter.test(KIND_RESOURCES_LIST))
        {
            caches.put(KIND_RESOURCES_LIST,
                new McpListCache(KIND_RESOURCES_LIST, STORE_KEY_RESOURCES, STORE_LOCK_KEY_RESOURCES));
        }
        if (filter.test(KIND_RESOURCES_TEMPLATES_LIST))
        {
            caches.put(KIND_RESOURCES_TEMPLATES_LIST,
                new McpListCache(KIND_RESOURCES_TEMPLATES_LIST, STORE_KEY_RESOURCES_TEMPLATES,
                    STORE_LOCK_KEY_RESOURCES_TEMPLATES));
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
        private final McpToolSearchIndex searchIndex;
        private final List<String> searchFields;
        private final byte[] searchToolBytes;
        private Map<CharSequence, List<String>> scopesByName = Collections.emptyMap();
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

        public Map<CharSequence, List<String>> scopesByName()
        {
            return scopesByName;
        }

        public McpToolSearchIndex searchIndex()
        {
            return searchIndex;
        }

        public byte[] searchToolBytes()
        {
            return searchToolBytes;
        }

        private McpListCache(
            int kind,
            String storeKey,
            String storeLockKey)
        {
            this(kind, storeKey, storeLockKey, null, null, null);
        }

        private McpListCache(
            int kind,
            String storeKey,
            String storeLockKey,
            McpToolSearchIndex searchIndex,
            List<String> searchFields,
            byte[] searchToolBytes)
        {
            this.kind = kind;
            this.storeKey = storeKey;
            this.storeLockKey = storeLockKey;
            this.fragments = new TreeMap<>();
            this.searchIndex = searchIndex;
            this.searchFields = searchFields;
            this.searchToolBytes = searchToolBytes;
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
            scopesByName = indexScopesByName(value);
            if (searchIndex != null)
            {
                searchIndex.index(McpToolSearchDocumentScanner.scan(value, searchFields));
            }
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
                scopesByName = indexScopesByName(value);
                if (searchIndex != null)
                {
                    searchIndex.index(McpToolSearchDocumentScanner.scan(value, searchFields));
                }
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

        private Map<CharSequence, List<String>> indexScopesByName(
            String value)
        {
            final Map<CharSequence, List<String>> index = new TreeMap<>(CharSequence::compare);

            if (kind == KIND_TOOLS_LIST && value != null)
            {
                final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                final JsonParserEx parser = JsonEx.createParser();
                parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
                try
                {
                    scanToolsList(parser, index);
                }
                catch (Exception ex)
                {
                    index.clear();
                }
            }

            return index;
        }

        private void scanToolsList(
            JsonParserEx parser,
            Map<CharSequence, List<String>> index)
        {
            if (parser.hasNext() && parser.next() == JsonParser.Event.START_OBJECT)
            {
                int depth = 1;
                while (depth > 0 && parser.hasNext())
                {
                    final JsonParser.Event event = parser.next();
                    switch (event)
                    {
                    case START_OBJECT:
                    case START_ARRAY:
                        depth++;
                        break;
                    case END_OBJECT:
                    case END_ARRAY:
                        depth--;
                        break;
                    case KEY_NAME:
                        if (depth == 1 && "tools".contentEquals(parser.getStringView()))
                        {
                            scanTools(parser, index);
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        private void scanTools(
            JsonParserEx parser,
            Map<CharSequence, List<String>> index)
        {
            if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY)
            {
                boolean items = true;
                while (items && parser.hasNext())
                {
                    final JsonParser.Event event = parser.next();
                    switch (event)
                    {
                    case START_OBJECT:
                        scanTool(parser, index);
                        break;
                    case END_ARRAY:
                        items = false;
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        private void scanTool(
            JsonParserEx parser,
            Map<CharSequence, List<String>> index)
        {
            String name = null;
            List<String> roles = null;
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1)
                    {
                        final CharSequence key = parser.getStringView();
                        if ("name".contentEquals(key))
                        {
                            parser.next();
                            name = parser.getString();
                        }
                        else if ("securitySchemes".contentEquals(key))
                        {
                            roles = scanSchemes(parser);
                        }
                    }
                    break;
                default:
                    break;
                }
            }

            if (name != null && roles != null)
            {
                index.put(name, roles);
            }
        }

        // returns the required roles for the first decisive scheme: empty when schemes are present but
        // none constrain roles, or null (NO_AUTH sentinel) when a scheme declares no authorization
        private List<String> scanSchemes(
            JsonParserEx parser)
        {
            List<String> roles = EMPTY_ROLES;

            if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY)
            {
                boolean settled = false;
                boolean schemes = true;
                while (schemes && parser.hasNext())
                {
                    final JsonParser.Event event = parser.next();
                    switch (event)
                    {
                    case START_OBJECT:
                        final List<String> scheme = scanScheme(parser);
                        if (!settled && scheme != null)
                        {
                            roles = scheme == NO_AUTH ? null : scheme;
                            settled = true;
                        }
                        break;
                    case END_ARRAY:
                        schemes = false;
                        break;
                    default:
                        break;
                    }
                }
            }

            return roles;
        }

        private List<String> scanScheme(
            JsonParserEx parser)
        {
            boolean noauth = false;
            List<String> scopes = null;
            int depth = 1;
            while (depth > 0 && parser.hasNext())
            {
                final JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    break;
                case KEY_NAME:
                    if (depth == 1)
                    {
                        final CharSequence key = parser.getStringView();
                        if ("type".contentEquals(key))
                        {
                            parser.next();
                            noauth = "noauth".contentEquals(parser.getStringView());
                        }
                        else if ("scopes".contentEquals(key))
                        {
                            scopes = scanStrings(parser);
                        }
                    }
                    break;
                default:
                    break;
                }
            }

            return noauth ? NO_AUTH : scopes;
        }

        private List<String> scanStrings(
            JsonParserEx parser)
        {
            final List<String> result = new ArrayList<>();
            if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY)
            {
                boolean values = true;
                while (values && parser.hasNext())
                {
                    final JsonParser.Event event = parser.next();
                    switch (event)
                    {
                    case VALUE_STRING:
                        result.add(parser.getString());
                        break;
                    case END_ARRAY:
                        values = false;
                        break;
                    default:
                        break;
                    }
                }
            }
            return result;
        }
    }
}
