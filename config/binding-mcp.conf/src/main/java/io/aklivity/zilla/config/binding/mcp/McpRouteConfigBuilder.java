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
package io.aklivity.zilla.config.binding.mcp;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;

public final class McpRouteConfigBuilder<T> extends RouteConfigBuilder<T, McpRouteConfigBuilder<T>>
{
    McpRouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpRouteConfigBuilder<T>> thisType()
    {
        return (Class<McpRouteConfigBuilder<T>>) getClass();
    }

    public McpConditionConfigBuilder<McpRouteConfigBuilder<T>> when()
    {
        return new McpConditionConfigBuilder<>(this::when);
    }

    public McpWithConfigBuilder<McpRouteConfigBuilder<T>> with()
    {
        return new McpWithConfigBuilder<>(this::with);
    }
}
