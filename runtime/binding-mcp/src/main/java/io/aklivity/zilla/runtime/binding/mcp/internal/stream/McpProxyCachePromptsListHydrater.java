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

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpListCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class McpProxyCachePromptsListHydrater extends McpProxyCacheListHydrater
{
    McpProxyCachePromptsListHydrater(
        EngineContext context,
        long originId,
        long routedId,
        LongSupplier supplyAuthorization,
        Supplier<String> supplySessionId,
        Runnable onReady,
        Duration leaseTtl,
        Duration cacheTtl,
        McpListCache cache)
    {
        super(context, originId, routedId, supplyAuthorization, supplySessionId, onReady,
            leaseTtl, cacheTtl, cache);
    }

    @Override
    protected int signalId()
    {
        return SIGNAL_REFRESH_PROMPTS;
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder builder,
        String sessionId)
    {
        builder.promptsList(p -> p.sessionId(sessionId));
    }
}
