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
package io.aklivity.zilla.config.binding.mcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.time.Duration;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.VaultedConfig;

public class McpConfigTest
{
    @Test
    public void shouldForwardRefFromToolSearchIndexThroughCacheToolsSearch()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();

        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .index(new McpRefTestToolSearchIndexConfig(vaulted))
            .build();

        assertThat(search.refs(), hasItem(vaulted));
    }

    @Test
    public void shouldDefaultCacheToolsSearchRefsToEmpty()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder().build();

        assertThat(search.refs(), empty());
    }

    @Test
    public void shouldDefaultCacheToolsEagerRefsToEmpty()
    {
        McpToolsEagerConfig eager = McpNoneToolsEagerConfig.builder().build();

        assertThat(eager.refs(), empty());
    }

    @Test
    public void shouldForwardRefFromToolsEagerThroughCacheTools()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();

        McpCacheToolsConfig tools = McpCacheToolsConfig.builder()
            .eager(new McpRefTestToolsEagerConfig(vaulted))
            .build();

        assertThat(tools.refs(), hasItem(vaulted));
    }

    @Test
    public void shouldForwardRefFromCacheToolsSearchThroughCacheTools()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .index(new McpRefTestToolSearchIndexConfig(vaulted))
            .build();

        McpCacheToolsConfig tools = McpCacheToolsConfig.builder()
            .search(search)
            .build();

        assertThat(tools.refs(), hasItem(vaulted));
    }

    @Test
    public void shouldDefaultCacheToolsRefsToEmpty()
    {
        McpCacheToolsConfig tools = McpCacheToolsConfig.builder().build();

        assertThat(tools.refs(), empty());
    }

    @Test
    public void shouldForwardRefFromCacheToolsThroughCache()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .index(new McpRefTestToolSearchIndexConfig(vaulted))
            .build();
        McpCacheToolsConfig tools = McpCacheToolsConfig.builder()
            .search(search)
            .build();

        McpCacheConfig cache = McpCacheConfig.builder()
            .tools(tools)
            .build();

        assertThat(cache.refs(), hasItem(vaulted));
    }

    @Test
    public void shouldDefaultCacheRefsToEmpty()
    {
        McpCacheConfig cache = McpCacheConfig.builder().build();

        assertThat(cache.refs(), empty());
    }

    @Test
    public void shouldForwardRefFromCacheThroughOptions()
    {
        VaultedConfig vaulted = VaultedConfig.builder().name("vault0").build();
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .index(new McpRefTestToolSearchIndexConfig(vaulted))
            .build();
        McpCacheToolsConfig tools = McpCacheToolsConfig.builder()
            .search(search)
            .build();
        McpCacheConfig cache = McpCacheConfig.builder()
            .tools(tools)
            .build();

        McpOptionsConfig options = (McpOptionsConfig) McpOptionsConfig.builder()
            .cache(cache)
            .build();

        assertThat(options.refs(), hasItem(vaulted));
    }

    private static final class McpRefTestToolSearchIndexConfig extends McpToolSearchIndexConfig
    {
        private final List<NamedConfig> refs;

        McpRefTestToolSearchIndexConfig(
            NamedConfig ref)
        {
            super("test");
            this.refs = List.of(ref);
        }

        @Override
        public List<NamedConfig> refs()
        {
            return refs;
        }
    }

    private static final class McpRefTestToolsEagerConfig extends McpToolsEagerConfig
    {
        private final List<NamedConfig> refs;

        McpRefTestToolsEagerConfig(
            NamedConfig ref)
        {
            super("test");
            this.refs = List.of(ref);
        }

        @Override
        public List<NamedConfig> refs()
        {
            return refs;
        }
    }

    @Test
    public void shouldBuildElicitationWithDefaultCallback()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .build();

        assertThat(elicitation.callback, equalTo(McpElicitationConfig.DEFAULT_CALLBACK_PATH));
    }

    @Test
    public void shouldBuildElicitationWithTimeout()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .timeout(Duration.ofSeconds(30))
            .build();

        assertThat(elicitation.timeout, equalTo(Duration.ofSeconds(30)));
    }

    @Test
    public void shouldBuildElicitationWithCallback()
    {
        McpElicitationConfig elicitation = McpElicitationConfig.builder()
            .callback("oauth/callback")
            .build();

        assertThat(elicitation.callback, equalTo("oauth/callback"));
    }

    @Test
    public void shouldMapElicitation()
    {
        String callback = McpElicitationConfig.<String>builder(e -> e.callback)
            .callback("auth/complete")
            .build();

        assertThat(callback, equalTo("auth/complete"));
    }
}
