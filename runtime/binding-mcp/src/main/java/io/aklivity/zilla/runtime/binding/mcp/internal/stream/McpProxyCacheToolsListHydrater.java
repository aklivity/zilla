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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.time.Duration;

import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;

final class McpProxyCacheToolsListHydrater extends McpProxyCacheListHydrater
{
    McpProxyCacheToolsListHydrater(
        McpProxyCacheHydrater parent)
    {
        super(parent, KIND_TOOLS_LIST, SIGNAL_REFRESH_TOOLS);
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder b,
        String sessionId)
    {
        b.toolsList(t -> t.sessionId(sessionId));
    }

    @Override
    protected Duration ttl()
    {
        return parent.binding.options.cache.ttl != null ? parent.binding.options.cache.ttl.tools : null;
    }
}
