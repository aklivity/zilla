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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.json.StreamingJson;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class McpProxyResourcesListFactory extends McpProxyListFactory
{
    private static final List<String> RESOURCES_LIST_ITEM_JSON_PATH_INCLUDES = List.of("/resources/-/uri");

    private final JsonParserFactory parserFactory;
    private final DirectBuffer prelude =
        new UnsafeBuffer("{\"resources\":[".getBytes(StandardCharsets.UTF_8));

    McpProxyResourcesListFactory(
        McpConfiguration config,
        EngineContext context,
        LongFunction<McpBindingConfig> supplyBinding)
    {
        super(config, context, supplyBinding);
        this.parserFactory = StreamingJson.createParserFactory(
            Map.of(StreamingJson.PATH_INCLUDES, RESOURCES_LIST_ITEM_JSON_PATH_INCLUDES));
    }

    @Override
    protected int kind()
    {
        return McpBeginExFW.KIND_RESOURCES_LIST;
    }

    @Override
    protected void injectInitialBeginEx(
        McpBeginExFW.Builder b,
        String sessionId)
    {
        b.resourcesList(r -> r.sessionId(sessionId));
    }

    @Override
    protected void injectReplyBeginEx(
        McpBeginExFW.Builder b,
        String sessionId)
    {
        b.resourcesList(r -> r.sessionId(sessionId));
    }

    @Override
    protected DirectBuffer listReplyOpenPrelude()
    {
        return prelude;
    }

    @Override
    protected JsonParserFactory listItemParserFactory()
    {
        return parserFactory;
    }

    @Override
    protected String arrayKey()
    {
        return "resources";
    }

    @Override
    protected String idKey()
    {
        return "uri";
    }
}
