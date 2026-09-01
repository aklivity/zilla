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

import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;
import io.aklivity.zilla.config.engine.NamedConfig;

public final class McpCacheToolsConfig extends Config.Extensible
{
    public final McpCacheToolsSearchConfig search;
    public final List<McpToolsEagerConfig> eager;

    McpCacheToolsConfig(
        McpCacheToolsSearchConfig search,
        List<McpToolsEagerConfig> eager)
    {
        super(null, withSearchAndEager(search, eager));
        this.search = search;
        this.eager = eager;
    }

    // search and eager may each contribute their own named references; folding both in here lets
    // McpCacheConfig discover every name under tools generically via tools.refs()
    private static List<NamedConfig> withSearchAndEager(
        McpCacheToolsSearchConfig search,
        List<McpToolsEagerConfig> eager)
    {
        List<NamedConfig> all = new ArrayList<>();
        if (search != null)
        {
            all.addAll(search.refs());
        }
        if (eager != null)
        {
            for (McpToolsEagerConfig each : eager)
            {
                all.addAll(each.refs());
            }
        }
        return all;
    }

    public static McpCacheToolsConfigBuilder<McpCacheToolsConfig> builder()
    {
        return new McpCacheToolsConfigBuilder<>(identity());
    }

    public static <T> McpCacheToolsConfigBuilder<T> builder(
        Function<McpCacheToolsConfig, T> mapper)
    {
        return new McpCacheToolsConfigBuilder<>(mapper);
    }
}
