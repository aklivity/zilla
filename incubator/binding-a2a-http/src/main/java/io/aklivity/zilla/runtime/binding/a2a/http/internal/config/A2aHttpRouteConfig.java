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

package io.aklivity.zilla.runtime.binding.a2a.http.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.UnaryOperator.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpConditionConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class A2aHttpRouteConfig
{
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*\\}");
    private static final String ARGS_PREFIX = "args.";
    private static final String HEADER_PATH = ":path";

    public final long id;
    public final String skill;
    public final A2aHttpWithConfig with;
    public final List<GuardedConfig> guarded;
    public final List<String> argAccessors;
    public final String pathBase;
    public final List<A2aHttpQueryFragment> pathQueryFragments;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public A2aHttpRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.with = (A2aHttpWithConfig) route.with;
        this.guarded = route.guarded;
        this.authorized = route.authorized;

        final ConditionConfig when = route.when.isEmpty() ? null : route.when.get(0);
        final A2aHttpConditionConfig condition = (A2aHttpConditionConfig) when;
        this.skill = condition != null ? condition.skill : null;

        final List<String> args = new ArrayList<>();
        if (with != null && with.headers != null)
        {
            with.headers.values().forEach(value -> collectAccessors(value, args));
        }
        this.argAccessors = args;

        final String path = with != null && with.headers != null ? with.headers.get(HEADER_PATH) : null;
        final int query = path != null ? path.indexOf('?') : -1;
        this.pathBase = query >= 0 ? path.substring(0, query) : path;
        this.pathQueryFragments = query >= 0 ? pathQueryFragments(path.substring(query + 1)) : List.of();
    }

    public boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    public boolean appliesToSkill(
        String id)
    {
        return skill != null && skill.equals(id) || skill == null;
    }

    public Map<String, String> resolveHeaders(
        Map<String, String> args)
    {
        final Map<String, String> resolved = new LinkedHashMap<>();
        if (with != null && with.headers != null)
        {
            for (Map.Entry<String, String> entry : with.headers.entrySet())
            {
                final String name = entry.getKey();
                if (HEADER_PATH.equals(name))
                {
                    resolved.put(name, entry.getValue());
                }
                else
                {
                    final String value = interpolateStrict(entry.getValue(), args);
                    if (value != null)
                    {
                        resolved.put(name, value);
                    }
                }
            }
        }
        return resolved;
    }

    public String resolvePath(
        Map<String, String> args)
    {
        String result = interpolatePath(pathBase, args);

        if (!pathQueryFragments.isEmpty())
        {
            final StringBuilder queryString = new StringBuilder();
            for (A2aHttpQueryFragment fragment : pathQueryFragments)
            {
                final String resolved = fragment.optional()
                    ? resolveOptionalFragment(fragment, args)
                    : interpolatePath(fragment.template, args);
                if (resolved != null)
                {
                    if (queryString.length() > 0)
                    {
                        queryString.append('&');
                    }
                    queryString.append(resolved);
                }
            }
            result = queryString.length() > 0 ? result + "?" + queryString : result;
        }

        return result;
    }

    private static String resolveOptionalFragment(
        A2aHttpQueryFragment fragment,
        Map<String, String> args)
    {
        final String raw = rawValue(fragment.expression, args);
        return raw != null ? fragment.name + "=" + encode(raw) : null;
    }

    private static String interpolatePath(
        String template,
        Map<String, String> args)
    {
        String result = template;

        if (template != null && template.contains("${"))
        {
            final StringBuilder builder = new StringBuilder();
            int index = 0;
            while (index < template.length())
            {
                final int start = template.indexOf("${", index);
                if (start < 0)
                {
                    builder.append(template, index, template.length());
                    index = template.length();
                }
                else
                {
                    builder.append(template, index, start);
                    final int end = template.indexOf('}', start + 2);
                    if (end < 0)
                    {
                        builder.append(template, start, template.length());
                        index = template.length();
                    }
                    else
                    {
                        final String expression = template.substring(start + 2, end);
                        final String raw = rawValue(expression, args);
                        builder.append(raw != null ? encode(raw) : "");
                        index = end + 1;
                    }
                }
            }
            result = builder.toString();
        }

        return result;
    }

    private static String interpolateStrict(
        String template,
        Map<String, String> args)
    {
        String result = template;

        if (template != null && template.contains("${"))
        {
            final StringBuilder builder = new StringBuilder();
            int index = 0;
            boolean resolved = true;
            while (resolved && index < template.length())
            {
                final int start = template.indexOf("${", index);
                if (start < 0)
                {
                    builder.append(template, index, template.length());
                    index = template.length();
                }
                else
                {
                    builder.append(template, index, start);
                    final int end = template.indexOf('}', start + 2);
                    if (end < 0)
                    {
                        builder.append(template, start, template.length());
                        index = template.length();
                    }
                    else
                    {
                        final String expression = template.substring(start + 2, end);
                        final String raw = rawValue(expression, args);
                        if (raw == null)
                        {
                            resolved = false;
                        }
                        else
                        {
                            builder.append(encode(raw));
                            index = end + 1;
                        }
                    }
                }
            }
            result = resolved ? builder.toString() : null;
        }

        return result;
    }

    private static String rawValue(
        String expression,
        Map<String, String> args)
    {
        return expression.startsWith(ARGS_PREFIX) && args != null
            ? args.get(expression.substring(ARGS_PREFIX.length()))
            : null;
    }

    public static String encode(
        String value)
    {
        final StringBuilder builder = new StringBuilder();
        for (byte b : value.getBytes(UTF_8))
        {
            encodeByte(builder, b & 0xff);
        }
        return builder.toString();
    }

    private static void encodeByte(
        StringBuilder builder,
        int c)
    {
        if (c >= 'A' && c <= 'Z' ||
            c >= 'a' && c <= 'z' ||
            c >= '0' && c <= '9' ||
            c == '-' || c == '.' || c == '_' || c == '~')
        {
            builder.append((char) c);
        }
        else
        {
            builder.append('%');
            builder.append(Character.toUpperCase(Character.forDigit(c >> 4 & 0xf, 16)));
            builder.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
        }
    }

    private static void collectAccessors(
        String template,
        List<String> args)
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
            }
        }
    }

    private static List<A2aHttpQueryFragment> pathQueryFragments(
        String query)
    {
        final List<A2aHttpQueryFragment> fragments = new ArrayList<>();
        for (String fragment : query.split("&"))
        {
            if (fragment.startsWith("${?") && fragment.endsWith("}"))
            {
                final String inner = fragment.substring(3, fragment.length() - 1);
                final int equals = inner.indexOf('=');
                fragments.add(A2aHttpQueryFragment.optional(inner.substring(0, equals), inner.substring(equals + 1)));
            }
            else
            {
                fragments.add(A2aHttpQueryFragment.required(fragment));
            }
        }
        return fragments;
    }
}
