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
package io.aklivity.zilla.config.binding.mcp.schema.registry;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class McpSchemaRegistryConditionConfig extends ConditionConfig
{
    public final String tool;

    public static McpSchemaRegistryConditionConfigBuilder<McpSchemaRegistryConditionConfig> builder()
    {
        return new McpSchemaRegistryConditionConfigBuilder<>(McpSchemaRegistryConditionConfig.class::cast);
    }

    public static <T> McpSchemaRegistryConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new McpSchemaRegistryConditionConfigBuilder<>(mapper);
    }

    McpSchemaRegistryConditionConfig(
        String tool)
    {
        this.tool = tool;
    }
}
