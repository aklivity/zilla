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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.config;

import java.util.function.ToLongBiFunction;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class McpSchemaRegistryBindingConfig
{
    public final long id;
    public final String namespace;
    public final String qname;
    public final KindConfig kind;
    public final McpSchemaRegistryOptionsConfig options;

    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;

    public transient McpSchemaRegistryCompositeConfig composite;

    public McpSchemaRegistryBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.kind = binding.kind;
        this.options = (McpSchemaRegistryOptionsConfig) binding.options;

        this.supplyBindingId = context::supplyBindingId;
    }
}
