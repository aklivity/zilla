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

import static java.nio.charset.StandardCharsets.UTF_8;
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
    private static final String HEADER_PATH = ":path";

    public final long id;
    public final String tool;
    public final String resource;
    public final McpHttpWithConfig with;
    public final List<GuardedConfig> guarded;
    public final List<String> bodyTemplatePointers;
    public final Map<String, String> bodyTemplateTargets;
    public final List<String> argAccessors;
    public final List<String> paramAccessors;
    public final String pathBase;
    public final List<McpHttpQueryFragment> pathQueryFragments;

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

        final Map<String, String> bodyTemplate = with != null && with.body != null ? with.body.template : null;
        if (bodyTemplate != null)
        {
            final List<String> pointers = new ArrayList<>();
            final Map<String, String> targets = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : bodyTemplate.entrySet())
            {
                final String target = entry.getKey();
                final Matcher matcher = ARGS_PATTERN.matcher(entry.getValue().trim());
                if (matcher.matches())
                {
                    final String accessor = matcher.group(1);
                    pointers.add(pointer(accessor));
                    targets.put(accessor, target);
                }
            }
            this.bodyTemplatePointers = pointers;
            this.bodyTemplateTargets = targets;
        }
        else
        {
            this.bodyTemplatePointers = null;
            this.bodyTemplateTargets = null;
        }

        final List<String> args = new ArrayList<>();
        final List<String> params = new ArrayList<>();
        if (with != null)
        {
            if (with.headers != null)
            {
                with.headers.values().forEach(value -> collectAccessors(value, args, params));
            }
            if (with.cookies != null)
            {
                with.cookies.values().forEach(value -> collectAccessors(value, args, params));
            }
            if (bodyTemplate != null)
            {
                bodyTemplate.values().forEach(value -> collectAccessors(value, args, params));
            }
        }
        this.argAccessors = args;
        this.paramAccessors = params;

        final String path = with != null && with.headers != null ? with.headers.get(HEADER_PATH) : null;
        final int query = path != null ? path.indexOf('?') : -1;
        this.pathBase = query >= 0 ? path.substring(0, query) : path;
        this.pathQueryFragments = query >= 0 ? pathQueryFragments(path.substring(query + 1)) : List.of();
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

    // Resolves every header other than :path (handled separately via resolvePath, since it also
    // carries the query string). A header whose value references an args./params. accessor that is
    // absent is omitted entirely, not sent with an empty value -- unlike interpolatePath (used for
    // :path and query fragments), which always has a value to substitute into.
    public Map<String, String> resolveHeaders(
        Map<String, String> args,
        Map<String, String> params)
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
                    final String value = interpolateStrict(entry.getValue(), args, params);
                    if (value != null)
                    {
                        resolved.put(name, value);
                    }
                }
            }
        }
        return resolved;
    }

    // Unlike resolveHeaders, where each header is independently included or omitted, cookies must
    // aggregate into a single Cookie header value -- so a cookie whose accessor is unresolved is just
    // dropped from the aggregate rather than causing the whole header to be omitted; only when every
    // configured cookie is unresolved does this return null (no Cookie header at all).
    public String resolveCookies(
        Map<String, String> args,
        Map<String, String> params)
    {
        String result = null;
        if (with != null && with.cookies != null)
        {
            final StringBuilder cookie = new StringBuilder();
            for (Map.Entry<String, String> entry : with.cookies.entrySet())
            {
                final String value = interpolateStrict(entry.getValue(), args, params);
                if (value != null)
                {
                    if (cookie.length() > 0)
                    {
                        cookie.append("; ");
                    }
                    cookie.append(entry.getKey()).append('=').append(value);
                }
            }
            result = cookie.length() > 0 ? cookie.toString() : null;
        }
        return result;
    }

    public String resolvePath(
        Map<String, String> args,
        Map<String, String> params)
    {
        String result = interpolatePath(pathBase, args, params);

        if (!pathQueryFragments.isEmpty())
        {
            final StringBuilder queryString = new StringBuilder();
            for (McpHttpQueryFragment fragment : pathQueryFragments)
            {
                final String resolved = fragment.optional()
                    ? resolveOptionalFragment(fragment, args, params)
                    : interpolatePath(fragment.template, args, params);
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
        McpHttpQueryFragment fragment,
        Map<String, String> args,
        Map<String, String> params)
    {
        final String raw = rawValue(fragment.expression, args, params);
        return raw != null ? fragment.name + "=" + encode(raw) : null;
    }

    private static String interpolatePath(
        String template,
        Map<String, String> args,
        Map<String, String> params)
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
                        final String raw = rawValue(expression, args, params);
                        builder.append(raw != null ? encode(raw) : "");
                        index = end + 1;
                    }
                }
            }
            result = builder.toString();
        }

        return result;
    }

    // Like interpolatePath, but returns null (instead of substituting an empty string) if any
    // referenced accessor is absent -- the signal resolveHeaders uses to omit a header entirely
    // rather than send it with an empty value.
    private static String interpolateStrict(
        String template,
        Map<String, String> args,
        Map<String, String> params)
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
                        final String raw = rawValue(expression, args, params);
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
        Map<String, String> args,
        Map<String, String> params)
    {
        String value = null;
        if (expression.startsWith(ARGS_PREFIX) && args != null)
        {
            value = args.get(expression.substring(ARGS_PREFIX.length()));
        }
        else if (expression.startsWith(PARAMS_PREFIX) && params != null)
        {
            value = params.get(expression.substring(PARAMS_PREFIX.length()));
        }
        return value;
    }

    // ASCII input (the common case for tool args, ids, route params) is percent-encoded by iterating
    // chars directly, skipping the UTF-8 byte conversion the general case requires below: a single-byte
    // ASCII char and its UTF-8 byte are bit-identical, so this produces the same output either way.
    public static String encode(
        String value)
    {
        final StringBuilder builder = new StringBuilder();
        if (isAscii(value))
        {
            for (int i = 0; i < value.length(); i++)
            {
                encodeByte(builder, value.charAt(i));
            }
        }
        else
        {
            final byte[] bytes = value.getBytes(UTF_8);
            for (byte b : bytes)
            {
                encodeByte(builder, b & 0xff);
            }
        }
        return builder.toString();
    }

    private static boolean isAscii(
        String value)
    {
        boolean ascii = true;
        for (int i = 0; ascii && i < value.length(); i++)
        {
            ascii = value.charAt(i) < 0x80;
        }
        return ascii;
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

    private static List<McpHttpQueryFragment> pathQueryFragments(
        String query)
    {
        final List<McpHttpQueryFragment> fragments = new ArrayList<>();
        for (String fragment : query.split("&"))
        {
            if (fragment.startsWith("${?") && fragment.endsWith("}"))
            {
                final String inner = fragment.substring(3, fragment.length() - 1);
                final int equals = inner.indexOf('=');
                fragments.add(McpHttpQueryFragment.optional(inner.substring(0, equals), inner.substring(equals + 1)));
            }
            else
            {
                fragments.add(McpHttpQueryFragment.required(fragment));
            }
        }
        return fragments;
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
