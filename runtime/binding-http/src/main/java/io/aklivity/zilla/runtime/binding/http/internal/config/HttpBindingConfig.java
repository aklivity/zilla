/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.SAME_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.DEFAULT_CREDENTIALS;
import static java.util.EnumSet.allOf;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpPatternConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class HttpBindingConfig
{
    private static final SortedSet<HttpVersion> DEFAULT_VERSIONS = new TreeSet<>(allOf(HttpVersion.class));
    private static final HttpAccessControlConfig DEFAULT_ACCESS_CONTROL = new HttpAccessControlConfig(SAME_ORIGIN);

    public final long id;
    public final String entry;
    public final HttpOptionsConfig options;
    public final KindConfig kind;
    public final List<HttpRouteConfig> routes;
    public final ToLongFunction<String> resolveId;
    public final Function<Function<String, String>, String> credentials;

    public HttpBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = HttpOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(HttpRouteConfig::new).collect(toList());
        this.resolveId = binding.resolveId;
        this.credentials = options != null && options.authorization != null ?
                asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
    }

    public HttpRouteConfig resolve(
        long authorization,
        Function<String, String> headerByName)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(headerByName))
            .findFirst()
            .orElse(null);
    }

    public SortedSet<HttpVersion>  versions()
    {
        return options != null && options.versions != null ? options.versions : DEFAULT_VERSIONS;
    }

    public HttpAccessControlConfig access()
    {
        return options != null && options.access != null ? options.access : DEFAULT_ACCESS_CONTROL;
    }

    public Function<Function<String, String>, String> credentials()
    {
        return credentials;
    }

    private Function<Function<String, String>, String> asAccessor(
            HttpCredentialsConfig credentials)
    {
        Function<Function<String, String>, String> accessor = DEFAULT_CREDENTIALS;
        List<HttpPatternConfig> headers = credentials.headers;
        List<HttpPatternConfig> parameters = credentials.parameters;
        List<HttpPatternConfig> cookies = credentials.cookies;

        if (cookies != null && !cookies.isEmpty())
        {
            HttpPatternConfig config = cookies.get(0);
            String cookieName = config.name;

            Matcher cookieMatch =
                    Pattern.compile(String.format(
                                    "(;\\s*)?%s=%s",
                                    cookieName,
                                         config.pattern.replace("{credentials}", "(?<credentials>[^\\s]+)")))
                            .matcher("");

            accessor = orElseIfNull(accessor, hs ->
            {
                String cookie = hs.apply("cookie");
                return cookie != null && cookieMatch.reset(cookie).find()
                        ? cookieMatch.group("credentials")
                        : null;
            });
        }

        if (headers != null && !headers.isEmpty())
        {
            HttpPatternConfig config = headers.get(0);
            String headerName = config.name;
            Matcher headerMatch =
                    Pattern.compile(config.pattern.replace("{credentials}", "(?<credentials>[^\\s]+)"))
                            .matcher("");

            accessor = orElseIfNull(accessor, hs ->
            {
                String header = hs.apply(headerName);
                return header != null && headerMatch.reset(header).matches()
                        ? headerMatch.group("credentials")
                        : null;
            });
        }

        if (parameters != null && !parameters.isEmpty())
        {
            HttpPatternConfig config = parameters.get(0);
            String parametersName = config.name;

            Matcher parametersMatch =
                    Pattern.compile(String.format(
                                    "(\\?|\\&)%s=%s",
                                    parametersName,
                                    config.pattern.replace("{credentials}", "(?<credentials>[^\\&]+)")))
                            .matcher("");

            accessor = orElseIfNull(accessor, hs ->
            {
                String pathWithQuery = hs.apply(":path");
                return pathWithQuery != null && parametersMatch.reset(pathWithQuery).find()
                        ? parametersMatch.group("credentials")
                        : null;
            });
        }

        return accessor;
    }

    private static Function<Function<String, String>, String> orElseIfNull(
            Function<Function<String, String>, String> first,
            Function<Function<String, String>, String> second)
    {
        return hs ->
        {
            String result = first.apply(hs);
            return result != null ? result : second.apply(hs);
        };
    }
}
