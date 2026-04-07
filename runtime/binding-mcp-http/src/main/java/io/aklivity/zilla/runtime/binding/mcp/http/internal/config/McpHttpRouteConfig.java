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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class McpHttpRouteConfig
{
    private static final Pattern ARGS_PATTERN = Pattern.compile("\\$\\{args\\.([^}]+)\\}");

    public final long id;
    public final McpHttpWithConfig with;

    private final List<McpHttpConditionConfig> when;

    public McpHttpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(McpHttpConditionConfig.class::cast)
            .collect(toList());
        this.with = route.with != null ? (McpHttpWithConfig) route.with : null;
    }

    boolean matches(
        String tool)
    {
        return when.isEmpty() || when.stream().anyMatch(w -> matchesTool(w, tool));
    }

    private boolean matchesTool(
        McpHttpConditionConfig condition,
        String tool)
    {
        return condition.tool == null || condition.tool.equals(tool);
    }

    public String resolveMethod()
    {
        return with != null ? with.method : null;
    }

    public Map<String, String> resolveHeaders(
        Map<String, String> args)
    {
        if (with == null || with.headers == null)
        {
            return null;
        }

        if (args == null || args.isEmpty())
        {
            return with.headers;
        }

        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : with.headers.entrySet())
        {
            resolved.put(entry.getKey(), resolveExpression(entry.getValue(), args));
        }
        return resolved;
    }

    private String resolveExpression(
        String template,
        Map<String, String> args)
    {
        StringBuffer result = new StringBuffer();
        Matcher matcher = ARGS_PATTERN.matcher(template);
        while (matcher.find())
        {
            String argName = matcher.group(1);
            String value = args.getOrDefault(argName, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
