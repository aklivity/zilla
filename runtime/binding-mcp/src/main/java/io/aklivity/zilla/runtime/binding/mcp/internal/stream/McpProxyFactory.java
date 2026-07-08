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
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_CALL;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.util.function.Function;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.mcp.internal.McpConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.internal.config.McpBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCache;
import io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache.McpProxyCacheManager;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
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
    private final Long2ObjectHashMap<McpProxyCacheManager> managers;
    private final Int2ObjectHashMap<BindingHandler> factories;
    private final Function<McpProxyCache, McpProxyCacheManager> supplyManager;

    public McpProxyFactory(
        McpConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
        this.managers = new Long2ObjectHashMap<>();
        this.factories = new Int2ObjectHashMap<>();

        final McpProxyLifecycleFactory lifecycleFactory =
            new McpProxyLifecycleFactory(config, context, bindings::get);
        final McpProxyToolsListFactory toolsListFactory =
            new McpProxyToolsListFactory(config, context, bindings::get);
        final McpProxyPromptsListFactory promptsListFactory =
            new McpProxyPromptsListFactory(config, context, bindings::get);
        final McpProxyResourcesListFactory resourcesListFactory =
            new McpProxyResourcesListFactory(config, context, bindings::get);
        final McpProxyResourcesTemplatesListFactory resourcesTemplatesListFactory =
            new McpProxyResourcesTemplatesListFactory(config, context, bindings::get);

        final Int2ObjectHashMap<McpProxyListFactory> listFactories = new Int2ObjectHashMap<>();
        listFactories.put(KIND_TOOLS_LIST, toolsListFactory);
        listFactories.put(KIND_PROMPTS_LIST, promptsListFactory);
        listFactories.put(KIND_RESOURCES_LIST, resourcesListFactory);
        listFactories.put(KIND_RESOURCES_TEMPLATES_LIST, resourcesTemplatesListFactory);

        final McpProxyCacheHydrater hydrater = new McpProxyCacheHydrater(
            context.bufferPool(), context::supplyTraceId, bindings::get, lifecycleFactory, listFactories);
        this.supplyManager = new McpProxyCacheManager.Factory(hydrater, context.signaler())::create;

        this.factories.put(KIND_LIFECYCLE, lifecycleFactory);
        this.factories.put(KIND_TOOLS_CALL,
            new McpProxyToolsCallFactory(config, context, bindings::get));
        this.factories.put(KIND_PROMPTS_GET,
            new McpProxyPromptsGetFactory(config, context, bindings::get));
        this.factories.put(KIND_RESOURCES_READ,
            new McpProxyResourcesReadFactory(config, context, bindings::get));
        this.factories.put(KIND_TOOLS_LIST, toolsListFactory);
        this.factories.put(KIND_PROMPTS_LIST, promptsListFactory);
        this.factories.put(KIND_RESOURCES_LIST, resourcesListFactory);
        this.factories.put(KIND_RESOURCES_TEMPLATES_LIST, resourcesTemplatesListFactory);
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
        if (newBinding.cache != null)
        {
            newBinding.cache.onSettled = (kind, changed, value) ->
            {
                if (kind == KIND_TOOLS_LIST && newBinding.validatesTools())
                {
                    newBinding.rebuildToolSchemaIndex(value);
                }
                if (changed)
                {
                    final long traceId = context.supplyTraceId();
                    for (var session : newBinding.sessions.values())
                    {
                        session.doNotifyListChanged(kind, traceId);
                    }
                }
            };
            McpProxyCacheManager manager = supplyManager.apply(newBinding.cache);
            managers.put(binding.id, manager);
            manager.start();
        }
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
        McpProxyCacheManager manager = managers.remove(bindingId);

        if (manager != null)
        {
            manager.stop();
        }
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
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
