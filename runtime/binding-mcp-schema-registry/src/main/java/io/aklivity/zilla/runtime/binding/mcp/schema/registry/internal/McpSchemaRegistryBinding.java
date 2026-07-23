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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal;

import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;

public final class McpSchemaRegistryBinding implements Binding
{
    public static final String TYPE = "mcp_schema_registry";

    private final McpSchemaRegistryConfiguration config;

    McpSchemaRegistryBinding(
        McpSchemaRegistryConfiguration config)
    {
        this.config = config;
    }

    @Override
    public String name()
    {
        return McpSchemaRegistryBinding.TYPE;
    }

    @Override
    public String originType(
        KindConfig kind)
    {
        return kind == KindConfig.CLIENT ? TYPE : null;
    }

    @Override
    public String routedType(
        KindConfig kind)
    {
        return kind == KindConfig.CLIENT ? TYPE : null;
    }

    @Override
    public McpSchemaRegistryBindingContext supply(
        EngineContext context)
    {
        return new McpSchemaRegistryBindingContext(config, context);
    }
}
