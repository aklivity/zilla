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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal;

import io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream.McpSchemaRegistryClientFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class McpSchemaRegistryBindingContext implements BindingContext
{
    private final McpSchemaRegistryClientFactory factory;

    McpSchemaRegistryBindingContext(
        McpSchemaRegistryConfiguration config,
        EngineContext context)
    {
        this.factory = new McpSchemaRegistryClientFactory(config, context);
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        BindingHandler handler = null;
        if (binding.kind == KindConfig.CLIENT)
        {
            factory.attach(binding);
            handler = factory;
        }
        return handler;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        if (binding.kind == KindConfig.CLIENT)
        {
            factory.detach(binding.id);
        }
    }
}
