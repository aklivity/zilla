/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.SAME_ORIGIN;
import static java.util.EnumSet.allOf;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public final class HttpBindingConfig
{
    private static final Function<Function<String, String>, String> DEFAULT_CREDENTIALS = f -> null;
    private static final SortedSet<HttpVersion> DEFAULT_VERSIONS = new TreeSet<>(allOf(HttpVersion.class));
    private static final HttpAccessControlConfig DEFAULT_ACCESS_CONTROL =
            HttpAccessControlConfig.builder().policy(SAME_ORIGIN).build();

    public final long id;
    public final String name;
    public final HttpOptionsConfig options;
    public final KindConfig kind;
    public final List<HttpRouteConfig> routes;
    public final ToLongFunction<String> resolveId;
    public final Function<Function<String, String>, String> credentials;
    public final List<HttpRequestType> requests;

    public HttpBindingConfig(
        BindingConfig binding)
    {
        this(binding, null);
    }

    public HttpBindingConfig(
        BindingConfig binding,
        BiFunction<ValidatorConfig, ToLongFunction<String>, Validator> createValidator)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = HttpOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(HttpRouteConfig::new).collect(toList());
        this.resolveId = binding.resolveId;
        this.credentials = options != null && options.authorization != null ?
                asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
        this.requests = createValidator == null ? null : createRequests(createValidator);
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
                String result = null;
                if (header != null && headerMatch.reset(header).matches())
                {
                    result = headerMatch.group("credentials");
                }
                return result;
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
                String result = null;
                if (pathWithQuery != null && parametersMatch.reset(pathWithQuery).find())
                {
                    result = parametersMatch.group("credentials");
                }
                return result;
            });
        }

        return accessor;
    }

    private List<HttpRequestType> createRequests(
        BiFunction<ValidatorConfig, ToLongFunction<String>, Validator> createValidator)
    {
        List<HttpRequestType> result = new LinkedList<>();
        if (this.options != null && this.options.requests != null)
        {
            for (HttpRequestConfig request : this.options.requests)
            {
                HttpRequestType requestType = new HttpRequestType();
                requestType.path = request.path;
                requestType.method = request.method;
                requestType.contentType = request.contentType;
                if (request.headers != null)
                {
                    requestType.headers = new HashMap<>();
                    for (HttpParamConfig header : request.headers)
                    {
                        String8FW name = new String8FW(header.name);
                        requestType.headers.put(name, createValidator.apply(header.validator, this.resolveId));
                    }
                }
                if (request.pathParams != null)
                {
                    requestType.pathParams = new HashMap<>();
                    for (HttpParamConfig pathParam : request.pathParams)
                    {
                        requestType.pathParams.put(pathParam.name, createValidator.apply(pathParam.validator, this.resolveId));
                    }
                }
                if (request.queryParams != null)
                {
                    requestType.queryParams = new HashMap<>();
                    for (HttpParamConfig queryParam : request.queryParams)
                    {
                        requestType.queryParams.put(queryParam.name, createValidator.apply(queryParam.validator, this.resolveId));
                    }
                }
                if (request.content != null)
                {
                    requestType.content = createValidator.apply(request.content, this.resolveId);
                }
                result.add(requestType);
            }
        }
        return result;
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
