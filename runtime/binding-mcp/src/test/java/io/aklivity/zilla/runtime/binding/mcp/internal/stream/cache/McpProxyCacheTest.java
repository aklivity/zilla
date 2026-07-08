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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
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
}
