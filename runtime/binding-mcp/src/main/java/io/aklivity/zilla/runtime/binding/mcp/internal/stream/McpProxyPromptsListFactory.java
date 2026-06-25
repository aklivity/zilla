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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class McpProxyPromptsListFactory extends McpProxyListFactory
{
    private static final List<String> PROMPTS_LIST_ITEM_JSON_PATH_INCLUDES = List.of("/prompts/-/name");

    private final DirectBufferEx prelude =
        new UnsafeBufferEx("{\"prompts\":[".getBytes(StandardCharsets.UTF_8));

    McpProxyPromptsListFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding)
    {
        super(config, context, supplyBinding, McpBeginExFW.KIND_PROMPTS_LIST, PROMPTS_LIST_ITEM_JSON_PATH_INCLUDES);
    }

    @Override
    protected McpProxyCache.McpListCache cacheOf(
        McpBindingConfig binding)
    {
        return binding.cache != null ? binding.cache.cacheOf(KIND_PROMPTS_LIST) : null;
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId)
    {
        builder.promptsList(p -> p.sessionId(sessionId));
    }

    @Override
    protected void injectReplyBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId)
    {
        builder.promptsList(p -> p.sessionId(sessionId));
    }

    @Override
    protected DirectBufferEx listReplyOpenPrelude()
    {
        return prelude;
    }

    @Override
    protected String arrayKey()
    {
        return "prompts";
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
        return beginEx.promptsList().sessionId().asString();
    }
}
