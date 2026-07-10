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

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class McpSchemaRegistryConditionConfigBuilder<T> extends
    ConfigBuilder<T, McpSchemaRegistryConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String tool;

    McpSchemaRegistryConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpSchemaRegistryConditionConfigBuilder<T>> thisType()
    {
        return (Class<McpSchemaRegistryConditionConfigBuilder<T>>) getClass();
    }

    public McpSchemaRegistryConditionConfigBuilder<T> tool(
        String tool)
    {
        this.tool = tool;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpSchemaRegistryConditionConfig(tool));
    }
}
