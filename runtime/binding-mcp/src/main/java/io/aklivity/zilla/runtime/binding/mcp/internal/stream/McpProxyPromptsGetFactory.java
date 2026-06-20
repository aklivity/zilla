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

import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class McpProxyPromptsGetFactory extends McpProxyItemFactory
{
    McpProxyPromptsGetFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding)
    {
        super(config, context, supplyBinding, McpBeginExFW.KIND_PROMPTS_GET);
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId,
        String identifier,
        int contentLength)
    {
        builder.promptsGet(p -> p.sessionId(sessionId).name(identifier).contentLength(contentLength));
    }

    @Override
    protected void injectReplyBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId,
        McpBeginExFW upstream)
    {
        builder.promptsGet(p -> p.sessionId(sessionId).name(upstream.promptsGet().name().asString()));
    }

    @Override
    protected String sessionId(
        McpBeginExFW beginEx)
    {
        return beginEx.promptsGet().sessionId().asString();
    }

    @Override
    protected int contentLength(
        McpBeginExFW beginEx)
    {
        return beginEx.promptsGet().contentLength();
    }
}
