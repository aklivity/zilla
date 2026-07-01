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

import static java.util.function.UnaryOperator.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpConditionConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpWithConfig;
import io.aklivity.zilla.runtime.engine.config.ConditionConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class McpHttpRouteConfig
{
    private static final Pattern ARGS_PATTERN = Pattern.compile("^\\$\\{\\s*args\\.([^}]+?)\\s*\\}$");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*\\}");
    private static final String ARGS_PREFIX = "args.";
    private static final String PARAMS_PREFIX = "params.";

    public final long id;
    public final String tool;
    public final String resource;
    public final McpHttpWithConfig with;
    public final List<GuardedConfig> guarded;
    public final List<String> bodyTemplatePointers;
    public final Map<String, String> bodyTemplateRenames;
    public final List<String> argAccessors;
    public final List<String> paramAccessors;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public McpHttpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = (McpHttpWithConfig) route.with;
        this.guarded = route.guarded;
        this.authorized = route.authorized;

        final ConditionConfig when = route.when.isEmpty() ? null : route.when.get(0);
        final McpHttpConditionConfig condition = (McpHttpConditionConfig) when;
        this.tool = condition != null ? condition.tool : null;
        this.resource = condition != null ? condition.resource : null;

        final Map<String, String> bodyTemplate = with != null ? with.bodyTemplate : null;
        if (bodyTemplate != null)
        {
            final List<String> pointers = new ArrayList<>();
            final Map<String, String> renames = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : bodyTemplate.entrySet())
            {
                final String target = entry.getKey();
                final Matcher matcher = ARGS_PATTERN.matcher(entry.getValue().trim());
                if (matcher.matches())
                {
                    final String accessor = matcher.group(1);
                    pointers.add(pointer(accessor));
                    final int dot = accessor.indexOf('.');
                    final String source = dot < 0 ? accessor : accessor.substring(0, dot);
                    if (!source.equals(target))
                    {
                        renames.put(source, target);
                    }
                }
            }
            this.bodyTemplatePointers = pointers;
            this.bodyTemplateRenames = renames;
        }
        else
        {
            this.bodyTemplatePointers = null;
            this.bodyTemplateRenames = null;
        }

        final List<String> args = new ArrayList<>();
        final List<String> params = new ArrayList<>();
        if (with != null)
        {
            if (with.headers != null)
            {
                with.headers.values().forEach(value -> collectAccessors(value, args, params));
            }
            if (bodyTemplate != null)
            {
                bodyTemplate.values().forEach(value -> collectAccessors(value, args, params));
            }
        }
        this.argAccessors = args;
        this.paramAccessors = params;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matchesTool(
        String name)
    {
        return tool != null && tool.equals(name);
    }

    boolean matchesResource(
        String name)
    {
        return resource != null && resource.equals(name);
    }

    private static void collectAccessors(
        String template,
        List<String> args,
        List<String> params)
    {
        if (template != null && template.contains("${"))
        {
            final Matcher matcher = EXPRESSION_PATTERN.matcher(template);
            while (matcher.find())
            {
                final String expression = matcher.group(1);
                if (expression.startsWith(ARGS_PREFIX))
                {
                    final String accessor = expression.substring(ARGS_PREFIX.length());
                    if (!args.contains(accessor))
                    {
                        args.add(accessor);
                    }
                }
                else if (expression.startsWith(PARAMS_PREFIX))
                {
                    final String accessor = expression.substring(PARAMS_PREFIX.length());
                    if (!params.contains(accessor))
                    {
                        params.add(accessor);
                    }
                }
            }
        }
    }

    private static String pointer(
        String accessor)
    {
        final StringBuilder pointer = new StringBuilder();
        for (String segment : accessor.split("\\."))
        {
            pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }
}
