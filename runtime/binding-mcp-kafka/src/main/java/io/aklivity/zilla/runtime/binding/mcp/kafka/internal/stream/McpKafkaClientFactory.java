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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.McpKafkaConfiguration;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaCompositeConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.McpKafkaRouteConfig;
import io.aklivity.zilla.runtime.binding.mcp.kafka.internal.config.composite.McpKafkaClientGenerator;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class McpKafkaClientFactory extends McpKafkaProxyFactory
{
    private final EngineContext context;
    private final McpKafkaClientGenerator generator;
    private final Long2ObjectHashMap<McpKafkaCompositeConfig> composites;

    public McpKafkaClientFactory(
        McpKafkaConfiguration config,
        EngineContext context)
    {
        super(config, context);
        this.context = context;
        this.generator = new McpKafkaClientGenerator(config.cacheClientExit());
        this.composites = new Long2ObjectHashMap<>();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        super.attach(binding);

        McpKafkaBindingConfig attached = bindings.get(binding.id);
        McpKafkaCompositeConfig composite = generator.generate(binding, context);
        for (McpKafkaRouteConfig route : attached.routes)
        {
            route.id = composite.exitId;
        }

        composite.namespaces.forEach(context::attachComposite);
        composites.put(binding.id, composite);
    }

    @Override
    public void detach(
        long bindingId)
    {
        McpKafkaCompositeConfig composite = composites.remove(bindingId);
        if (composite != null)
        {
            composite.namespaces.forEach(context::detachComposite);
        }

        super.detach(bindingId);
    }
}
