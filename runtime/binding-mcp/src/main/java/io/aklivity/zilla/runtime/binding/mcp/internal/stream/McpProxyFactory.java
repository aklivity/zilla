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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_LIFECYCLE;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_GET;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_READ;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class McpProxyFactory implements McpStreamFactory
{
    private static final String MCP_TYPE_NAME = "mcp";

    private final BeginFW beginRO = new BeginFW();
    private final McpBeginExFW mcpBeginExRO = new McpBeginExFW();

    private final McpConfiguration config;
    private final EngineContext context;
    private final int mcpTypeId;

    private final Long2ObjectHashMap<McpBindingConfig> bindings;
    private final Int2ObjectHashMap<BindingHandler> factories;
    private final McpProxyCacheHydrater hydrater;

    public McpProxyFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
        this.factories = new Int2ObjectHashMap<>();
        this.hydrater = new McpProxyCacheHydrater(config, context);
        this.factories.put(KIND_LIFECYCLE,
            new McpProxyLifecycleFactory(config, context, bindings::get));
        this.factories.put(KIND_TOOLS_CALL,
            new McpProxyToolsCallFactory(config, context, bindings::get));
        this.factories.put(KIND_PROMPTS_GET,
            new McpProxyPromptsGetFactory(config, context, bindings::get));
        this.factories.put(KIND_RESOURCES_READ,
            new McpProxyResourcesReadFactory(config, context, bindings::get));
        this.factories.put(KIND_TOOLS_LIST,
            new McpProxyToolsListFactory(config, context, bindings::get));
        this.factories.put(KIND_PROMPTS_LIST,
            new McpProxyPromptsListFactory(config, context, bindings::get));
        this.factories.put(KIND_RESOURCES_LIST,
            new McpProxyResourcesListFactory(config, context, bindings::get));
        this.mcpTypeId = context.supplyTypeId(MCP_TYPE_NAME);
    }

    @Override
    public int originTypeId()
    {
        return mcpTypeId;
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        McpBindingConfig newBinding = new McpBindingConfig(binding, config, context);
        bindings.put(binding.id, newBinding);
        if (newBinding.cacheContext != null)
        {
            hydrater.attach(newBinding.cacheContext);
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        McpBindingConfig binding = bindings.remove(bindingId);

        if (binding != null && binding.cacheContext != null)
        {
            hydrater.detach(binding.cacheContext);
        }
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final OctetsFW extension = begin.extension();

        MessageConsumer newStream = null;

        final McpBeginExFW beginEx = extension.get(mcpBeginExRO::tryWrap);

        if (beginEx != null)
        {
            final BindingHandler factory = factories.get(beginEx.kind());
            if (factory != null)
            {
                newStream = factory.newStream(msgTypeId, buffer, index, length, sender);
            }
        }

        return newStream;
    }
}
