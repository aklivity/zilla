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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import jakarta.json.stream.JsonParser;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheToolsEagerConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheToolsEagerPolicy;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpSearchToolDescriptor;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolByteRange;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolByteRangeScanner;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolSearchDocumentScanner;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolSearchIndexFactory;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
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

    // the JSON array key each kind's assembled list is wrapped in on the wire -- e.g. {"resources":[...]}
    // for KIND_RESOURCES_LIST -- matching McpProxyResourcesListFactory.arrayKey() and its siblings; distinct
    // from the STORE_KEY_* constants above, which name the store entry rather than the wire array key
    private static final String ARRAY_KEY_TOOLS = "tools";
    private static final String ARRAY_KEY_RESOURCES = "resources";
    private static final String ARRAY_KEY_RESOURCES_TEMPLATES = "resourceTemplates";
    private static final String ARRAY_KEY_PROMPTS = "prompts";
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
    public final Duration cacheTtl;

    public String sessionId;
    public long authorization;

    private final StoreHandler store;
    private final Int2ObjectHashMap<McpListCache> caches;
    private final Long2LongHashMap routeAuthorizations;
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
        this.cacheTtl = cache.ttl;
        this.awaiters = new ArrayList<>();
        this.caches = new Int2ObjectHashMap<>();
        this.routeAuthorizations = new Long2LongHashMap(-1L);

        final IntPredicate filter = config.hydrateFilter();
        if (filter.test(KIND_TOOLS_LIST))
        {
            final McpToolSearchIndex searchIndex = cache.tools != null
                ? new McpToolSearchIndexFactory().create(cache.tools.search)
                : null;
            final List<String> searchFields = cache.tools != null && cache.tools.search != null
                ? cache.tools.search.fields
                : null;
            final String searchToolkit = cache.tools != null && cache.tools.search != null
                ? cache.tools.search.toolkit
                : null;
            final byte[] searchToolsBytes = cache.tools != null && cache.tools.search != null
                ? McpSearchToolDescriptor.buildSearchTools(searchToolkit)
                : null;
            final byte[] describeToolBytes = cache.tools != null && cache.tools.search != null
                ? McpSearchToolDescriptor.buildDescribeTool(searchToolkit)
                : null;
            final byte[] executeToolBytes = cache.tools != null && cache.tools.search != null
                ? McpSearchToolDescriptor.buildExecuteTool(searchToolkit)
                : null;
            final McpCacheToolsEagerConfig eager = cache.tools != null ? cache.tools.eager : null;
            caches.put(KIND_TOOLS_LIST,
                new McpListCache(KIND_TOOLS_LIST, STORE_KEY_TOOLS, STORE_LOCK_KEY_TOOLS,
                    searchIndex, searchFields, searchToolsBytes, describeToolBytes, executeToolBytes, eager));
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

    // memoizes the reauthorized session for a route's own with.cache.credentials override, so
    // multiple south connections for the same route within one hydration attempt share one session
    // instead of each minting a fresh one from the guard
    public long routeAuthorization(
        long traceId,
        long routedId,
        String credentials)
    {
        return routeAuthorizations.computeIfAbsent(routedId, id -> guard.reauthorize(traceId, bindingId, 0L, credentials));
    }

    public void resetRouteAuthorizations()
    {
        routeAuthorizations.clear();
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

    // records that a kind's hydration cycle has completed at least once, success or failure -- this is
    // what lets checkReady()/register() unblock a brand-new session's initialize promptly even when every
    // route for this kind keeps failing, without ever needing to give up retrying in the background
    public void markAttempted(
        int kind)
    {
        final McpListCache cache = caches.get(kind);
        if (cache != null && !cache.attempted)
        {
            cache.attempted = true;
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
        private final byte[] searchToolsBytes;
        private final byte[] describeToolBytes;
        private final byte[] executeToolBytes;
        private final McpCacheToolsEagerPolicy eagerPolicy;
        private final List<Pattern> eagerMatch;
        private Map<CharSequence, List<String>> scopesByName = Collections.emptyMap();
        private Map<CharSequence, String> descriptionsByName = Collections.emptyMap();
        private Map<CharSequence, McpToolByteRange> toolRangesByName = Collections.emptyMap();
        private byte[] toolsBytes = new byte[0];
        private long lastChecksum = -1L;
        private String lockToken;

        boolean populated;
        boolean attempted;

        boolean settled()
        {
            return populated || attempted;
        }

        public Map<CharSequence, List<String>> scopesByName()
        {
            return scopesByName;
        }

        public Map<CharSequence, String> descriptionsByName()
        {
            return descriptionsByName;
        }

        public Map<CharSequence, McpToolByteRange> toolRangesByName()
        {
            return toolRangesByName;
        }

        public byte[] toolsBytes()
        {
            return toolsBytes;
        }

        public McpToolSearchIndex searchIndex()
        {
            return searchIndex;
        }

        public byte[] searchToolsBytes()
        {
            return searchToolsBytes;
        }

        public byte[] describeToolBytes()
        {
            return describeToolBytes;
        }

        public byte[] executeToolBytes()
        {
            return executeToolBytes;
        }

        public boolean eagerConfigured()
        {
            return eagerPolicy != null && eagerPolicy != McpCacheToolsEagerPolicy.NONE;
        }

        public boolean eager(
            CharSequence name)
        {
            return switch (eagerPolicy)
            {
            case ALL -> false;
            case EXPLICIT -> admitsEager(name);
            default -> true;
            };
        }

        private boolean admitsEager(
            CharSequence name)
        {
            boolean admitted = false;
            for (Pattern pattern : eagerMatch)
            {
                if (pattern.matcher(name).matches())
                {
                    admitted = true;
                    break;
                }
            }
            return admitted;
        }

        private McpListCache(
            int kind,
            String storeKey,
            String storeLockKey)
        {
            this(kind, storeKey, storeLockKey, null, null, null, null, null, null);
        }

        private McpListCache(
            int kind,
            String storeKey,
            String storeLockKey,
            McpToolSearchIndex searchIndex,
            List<String> searchFields,
            byte[] searchToolsBytes,
            byte[] describeToolBytes,
            byte[] executeToolBytes,
            McpCacheToolsEagerConfig eager)
        {
            this.kind = kind;
            this.storeKey = storeKey;
            this.storeLockKey = storeLockKey;
            this.fragments = new TreeMap<>();
            this.searchIndex = searchIndex;
            this.searchFields = searchFields;
            this.searchToolsBytes = searchToolsBytes;
            this.describeToolBytes = describeToolBytes;
            this.executeToolBytes = executeToolBytes;
            this.eagerPolicy = eager != null ? eager.policy : McpCacheToolsEagerPolicy.NONE;
            this.eagerMatch = eager != null && eager.match != null ? compileEagerMatch(eager.match) : null;
        }

        private List<Pattern> compileEagerMatch(
            List<String> globs)
        {
            return globs.stream()
                .map(McpListCache::compileGlob)
                .collect(Collectors.toList());
        }

        private static Pattern compileGlob(
            String glob)
        {
            final StringBuilder regex = new StringBuilder();
            final String[] literals = glob.split("\\*", -1);
            for (int index = 0; index < literals.length; index++)
            {
                if (index > 0)
                {
                    regex.append(".*");
                }
                if (!literals[index].isEmpty())
                {
                    regex.append(Pattern.quote(literals[index]));
                }
            }
            return Pattern.compile(regex.toString());
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
                final List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(value, searchFields);
                descriptionsByName = indexDescriptionsByName(documents);
                searchIndex.index(documents);
                toolsBytes = value.getBytes(StandardCharsets.UTF_8);
                toolRangesByName = McpToolByteRangeScanner.scan(toolsBytes);
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
                    final List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(value, searchFields);
                    descriptionsByName = indexDescriptionsByName(documents);
                    searchIndex.index(documents);
                    toolsBytes = value.getBytes(StandardCharsets.UTF_8);
                    toolRangesByName = McpToolByteRangeScanner.scan(toolsBytes);
                }
            }
            else
            {
                changed = false;
            }
            populated = value != null;
            checkReady();
            onSettled.accept(kind, changed, value);
        }

        private void checkPut(
            String key)
        {
            populated = true;
            checkReady();
        }

        private String arrayKeyOf(
            int kind)
        {
            final String arrayKey;
            if (kind == KIND_TOOLS_LIST)
            {
                arrayKey = ARRAY_KEY_TOOLS;
            }
            else if (kind == KIND_RESOURCES_LIST)
            {
                arrayKey = ARRAY_KEY_RESOURCES;
            }
            else if (kind == KIND_RESOURCES_TEMPLATES_LIST)
            {
                arrayKey = ARRAY_KEY_RESOURCES_TEMPLATES;
            }
            else if (kind == KIND_PROMPTS_LIST)
            {
                arrayKey = ARRAY_KEY_PROMPTS;
            }
            else
            {
                arrayKey = null;
            }
            return arrayKey;
        }

        // scopesByName drives McpScopeFilter for every list kind, not just tools -- a resource or prompt
        // guarded only by its toolkit route's own scope (no operation-level security of its own) carries
        // that scope in securitySchemes exactly like a tool does (see McpProxyCacheHydrater), so indexing
        // must cover every kind's own array key or such items would never be filtered at all
        private Map<CharSequence, List<String>> indexScopesByName(
            String value)
        {
            final Map<CharSequence, List<String>> index = new TreeMap<>(CharSequence::compare);

            final String arrayKey = arrayKeyOf(kind);
            if (arrayKey != null && value != null)
            {
                final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                final JsonParserEx parser = JsonEx.createParser();
                parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
                try
                {
                    scanList(parser, arrayKey, index);
                }
                catch (Exception ex)
                {
                    index.clear();
                }
            }

            return index;
        }

        // built from the same McpToolSearchDocumentScanner pass already fed to searchIndex.index(),
        // so a search result can surface a tool's description without re-parsing the cached tools/list
        private static Map<CharSequence, String> indexDescriptionsByName(
            List<McpToolSearchDocument> documents)
        {
            final Map<CharSequence, String> index = new TreeMap<>(CharSequence::compare);

            for (McpToolSearchDocument document : documents)
            {
                final String description = document.field("description");
                if (description != null)
                {
                    index.put(document.name, description);
                }
            }

            return index;
        }

        private void scanList(
            JsonParserEx parser,
            String arrayKey,
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
                        if (depth == 1 && arrayKey.contentEquals(parser.getStringView()))
                        {
                            scanItems(parser, index);
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }

        private void scanItems(
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
                        scanItem(parser, index);
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

        private void scanItem(
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
