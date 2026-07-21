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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.nio.charset.StandardCharsets;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class McpProxyToolsListFactory extends McpProxyListFactory
{
    private final DirectBufferEx prelude =
        new UnsafeBufferEx("{\"tools\":[".getBytes(StandardCharsets.UTF_8));

    McpProxyToolsListFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding)
    {
        super(config, context, supplyBinding, McpBeginExFW.KIND_TOOLS_LIST);
    }

    @Override
    protected McpProxyCache.McpListCache cacheOf(
        McpBindingConfig binding)
    {
        return binding.cache != null ? binding.cache.cacheOf(KIND_TOOLS_LIST) : null;
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId)
    {
        builder.toolsList(t -> t.sessionId(sessionId));
    }

    @Override
    protected void injectReplyBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId)
    {
        builder.toolsList(t -> t.sessionId(sessionId));
    }

    @Override
    protected DirectBufferEx listReplyOpenPrelude()
    {
        return prelude;
    }

    @Override
    protected String arrayKey()
    {
        return "tools";
    }

    @Override
    protected String idKey()
    {
        return "name";
    }

    @Override
    protected String sessionId(
        McpBeginExFW beginEx)
    {
        return beginEx.toolsList().sessionId().asString();
    }
}
