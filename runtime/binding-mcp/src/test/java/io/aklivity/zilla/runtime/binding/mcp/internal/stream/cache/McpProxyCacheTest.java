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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.search.McpToolByteRange;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache.McpListCache;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class McpProxyCacheTest
{
    private GuardHandler guard;
    private McpProxyCache cache;

    @Before
    public void setup()
    {
        guard = mock(GuardHandler.class);

        EngineContext context = mock(EngineContext.class);
        when(context.supplyStore(anyLong())).thenReturn(mock(StoreHandler.class));
        when(context.supplyGuard(anyLong())).thenReturn(guard);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp")
            .kind(KindConfig.PROXY)
            .build();
        binding.id = 1L;
        binding.resolveId = name -> 1L;

        McpCacheConfig cacheConfig = McpCacheConfig.builder()
            .store("memory0")
            .authorization()
                .name("test_guard")
                .credentials("{token}")
                .build()
            .build();

        cache = new McpProxyCache(binding, new McpConfiguration(), context, cacheConfig);
    }

    @Test
    public void shouldMemoizeRouteAuthorizationForSameRoute()
    {
        when(guard.reauthorize(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(7L, 9L);

        long first = cache.routeAuthorization(1L, 100L, "token-a");
        long second = cache.routeAuthorization(1L, 100L, "token-a");

        assertThat(first, equalTo(7L));
        assertThat(second, equalTo(7L));
        verify(guard, times(1)).reauthorize(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    public void shouldReauthorizeIndependentlyPerRoute()
    {
        when(guard.reauthorize(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(7L, 9L);

        long routeA = cache.routeAuthorization(1L, 100L, "token-a");
        long routeB = cache.routeAuthorization(1L, 200L, "token-b");

        assertThat(routeA, equalTo(7L));
        assertThat(routeB, equalTo(9L));
        verify(guard, times(2)).reauthorize(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    public void shouldReauthorizeAgainAfterReset()
    {
        when(guard.reauthorize(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(7L, 9L);

        cache.routeAuthorization(1L, 100L, "token-a");
        cache.resetRouteAuthorizations();
        cache.routeAuthorization(1L, 100L, "token-a");

        verify(guard, times(2)).reauthorize(anyLong(), anyLong(), anyLong(), anyString());
    }

    // scopesByName drives McpScopeFilter's per-caller admission check for every list kind, not just
    // tools -- a resource or prompt guarded only by its toolkit route's own scope (no operation-level
    // security of its own) carries that scope in securitySchemes exactly like a tool does (see
    // McpProxyCacheHydrater.injectRouteScopes), so indexing must cover each kind's own wire array key
    // ("tools", "resources", "resourceTemplates", "prompts") or such items are never filtered at all
    @Test
    public void shouldIndexScopesByNameForToolsList()
    {
        McpListCache tools = cache.cacheOf(KIND_TOOLS_LIST);
        String value = """
            {"tools":[{"name":"create_pet","securitySchemes":[{"type":"oauth2","scopes":["pets:write"]}]}]}""";

        tools.put(value, completion -> {});

        assertThat(tools.scopesByName().get("create_pet"), equalTo(of("pets:write")));
    }

    @Test
    public void shouldIndexScopesByNameForResourcesList()
    {
        McpListCache resources = cache.cacheOf(KIND_RESOURCES_LIST);
        String value = """
            {"resources":[{"name":"featured_pets","securitySchemes":[{"type":"oauth2","scopes":["petstore:tools"]}]}]}""";

        resources.put(value, completion -> {});

        assertThat(resources.scopesByName().get("featured_pets"), equalTo(of("petstore:tools")));
    }

    @Test
    public void shouldIndexScopesByNameForResourcesTemplatesList()
    {
        McpListCache templates = cache.cacheOf(KIND_RESOURCES_TEMPLATES_LIST);
        String value = """
            {"resourceTemplates":[{"name":"pet_by_id","securitySchemes":[{"type":"oauth2","scopes":["petstore:tools"]}]}]}""";

        templates.put(value, completion -> {});

        assertThat(templates.scopesByName().get("pet_by_id"), equalTo(of("petstore:tools")));
    }

    @Test
    public void shouldIndexScopesByNameForPromptsList()
    {
        McpListCache prompts = cache.cacheOf(KIND_PROMPTS_LIST);
        String value = """
            {"prompts":[{"name":"greeting","securitySchemes":[{"type":"oauth2","scopes":["petstore:tools"]}]}]}""";

        prompts.put(value, completion -> {});

        assertThat(prompts.scopesByName().get("greeting"), equalTo(of("petstore:tools")));
    }

    // toolRangesByName is the byte-copy source for a tools/search match's structuredContent entry --
    // each recorded range must exactly bound its tool's whole JSON object as it appears in the cached
    // bytes, verbatim spacing included, since McpToolSearchServer.buildResponse arraycopies straight
    // out of these ranges rather than re-serializing
    @Test
    public void shouldIndexToolByteRangesForSearch()
    {
        EngineContext context = mock(EngineContext.class);
        when(context.supplyStore(anyLong())).thenReturn(mock(StoreHandler.class));
        when(context.supplyGuard(anyLong())).thenReturn(guard);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp")
            .kind(KindConfig.PROXY)
            .build();
        binding.id = 1L;
        binding.resolveId = name -> 1L;

        McpCacheConfig cacheConfig = McpCacheConfig.builder()
            .store("memory0")
            .tools()
                .search()
                    .toolkit("zilla")
                    .fields(of("name", "description"))
                    .index(new McpKeywordToolSearchIndexConfig())
                    .build()
                .build()
            .build();

        McpProxyCache searchCache = new McpProxyCache(binding, new McpConfiguration(), context, cacheConfig);
        McpListCache tools = searchCache.cacheOf(KIND_TOOLS_LIST);

        String value = """
            {"tools":[{"name": "get_weather","description": "Get current weather"},\
            {"name": "github__delete_repo","description": "Delete a repository"}]}""";

        tools.put(value, completion -> {});

        byte[] bytes = tools.toolsBytes();
        Map<CharSequence, McpToolByteRange> ranges = tools.toolRangesByName();

        McpToolByteRange weather = ranges.get("get_weather");
        McpToolByteRange delete = ranges.get("github__delete_repo");

        assertThat(new String(bytes, weather.offset(), weather.length(), UTF_8),
            equalTo("{\"name\": \"get_weather\",\"description\": \"Get current weather\"}"));
        assertThat(new String(bytes, delete.offset(), delete.length(), UTF_8),
            equalTo("{\"name\": \"github__delete_repo\",\"description\": \"Delete a repository\"}"));

        assertThat(new String(bytes, weather.nameOffset(), weather.nameLength(), UTF_8), equalTo("get_weather"));
        assertThat(weather.hasDescription(), equalTo(true));
        assertThat(new String(bytes, weather.descriptionOffset(), weather.descriptionLength(), UTF_8),
            equalTo("Get current weather"));

        assertThat(new String(bytes, delete.nameOffset(), delete.nameLength(), UTF_8), equalTo("github__delete_repo"));
        assertThat(delete.hasDescription(), equalTo(true));
        assertThat(new String(bytes, delete.descriptionOffset(), delete.descriptionLength(), UTF_8),
            equalTo("Delete a repository"));
    }

    @Test
    public void shouldIndexToolByteRangeWithoutDescription()
    {
        EngineContext context = mock(EngineContext.class);
        when(context.supplyStore(anyLong())).thenReturn(mock(StoreHandler.class));
        when(context.supplyGuard(anyLong())).thenReturn(guard);

        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("app0")
            .type("mcp")
            .kind(KindConfig.PROXY)
            .build();
        binding.id = 1L;
        binding.resolveId = name -> 1L;

        McpCacheConfig cacheConfig = McpCacheConfig.builder()
            .store("memory0")
            .tools()
                .search()
                    .toolkit("zilla")
                    .fields(of("name", "description"))
                    .index(new McpKeywordToolSearchIndexConfig())
                    .build()
                .build()
            .build();

        McpProxyCache searchCache = new McpProxyCache(binding, new McpConfiguration(), context, cacheConfig);
        McpListCache tools = searchCache.cacheOf(KIND_TOOLS_LIST);

        String value = """
            {"tools":[{"name": "no_description_tool"}]}""";

        tools.put(value, completion -> {});

        Map<CharSequence, McpToolByteRange> ranges = tools.toolRangesByName();
        McpToolByteRange range = ranges.get("no_description_tool");

        assertThat(range.hasDescription(), equalTo(false));
    }
}
