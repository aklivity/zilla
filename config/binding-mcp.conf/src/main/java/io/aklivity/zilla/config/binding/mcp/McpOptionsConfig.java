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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpOptionsConfig extends OptionsConfig
{
    public final McpElicitationConfig elicitation;
    public final McpAuthorizationConfig authorization;
    public final McpCacheConfig cache;
    public final String server;
    public final ModelConfig tools;

    McpOptionsConfig(
        McpElicitationConfig elicitation,
        McpAuthorizationConfig authorization,
        McpCacheConfig cache,
        String server,
        ModelConfig tools)
    {
        this(elicitation, authorization, cache, server, tools, null, List.of());
    }

    McpOptionsConfig(
        McpElicitationConfig elicitation,
        McpAuthorizationConfig authorization,
        McpCacheConfig cache,
        String server,
        ModelConfig tools,
        Map<String, Config> extensions,
        List<NamedConfig> refs)
    {
        super(tools != null ? List.of(tools) : List.of(), List.of(), extensions, withToolSearchRefs(cache, refs));
        this.elicitation = elicitation;
        this.authorization = authorization;
        this.cache = cache;
        this.server = server;
        this.tools = tools;
    }

    // any configured tool search index (e.g. an externally-registered semantic backend) may itself
    // reference a named engine concept (e.g. an embeddings: entry) -- fold those refs in alongside
    // this options' own, so the engine resolves them with one generic walk
    private static List<NamedConfig> withToolSearchRefs(
        McpCacheConfig cache,
        List<NamedConfig> refs)
    {
        List<NamedConfig> all = new ArrayList<>();
        if (refs != null)
        {
            all.addAll(refs);
        }
        if (cache != null && cache.tools != null && cache.tools.search != null && cache.tools.search.indexes != null)
        {
            for (McpToolSearchIndexConfig index : cache.tools.search.indexes)
            {
                all.addAll(index.refs());
            }
        }
        return all;
    }

    public static McpOptionsConfigBuilder<McpOptionsConfig> builder()
    {
        return new McpOptionsConfigBuilder<>(McpOptionsConfig.class::cast);
    }

    public static <T> McpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new McpOptionsConfigBuilder<>(mapper);
    }
}
