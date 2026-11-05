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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import static java.util.function.UnaryOperator.identity;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import jakarta.json.JsonValue;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcConditionConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcWithConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class McpGrpcRouteConfig
{
    public final String tool;
    public final String method;
    public final Map<String, JsonValue> option;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public McpGrpcRouteConfig(
        String tool,
        String method,
        Map<String, JsonValue> option)
    {
        this(tool, method, option, null);
    }

    public McpGrpcRouteConfig(
        RouteConfig route)
    {
        this(tool(route.when), method(route.with), option(route.with), route.authorized);
    }

    private McpGrpcRouteConfig(
        String tool,
        String method,
        Map<String, JsonValue> option,
        LongObjectPredicate<UnaryOperator<String>> authorized)
    {
        this.tool = tool;
        this.method = method;
        this.option = option;
        this.authorized = authorized;
    }

    public boolean authorized(
        long authorization)
    {
        return authorized == null || authorized.test(authorization, identity());
    }

    private static String tool(
        List<? extends ConditionConfig> when)
    {
        return when != null && !when.isEmpty() ? ((McpGrpcConditionConfig) when.get(0)).tool : null;
    }

    private static String method(
        WithConfig with)
    {
        return with != null ? ((McpGrpcWithConfig) with).method : null;
    }

    private static Map<String, JsonValue> option(
        WithConfig with)
    {
        return with != null ? ((McpGrpcWithConfig) with).option : null;
    }
}
