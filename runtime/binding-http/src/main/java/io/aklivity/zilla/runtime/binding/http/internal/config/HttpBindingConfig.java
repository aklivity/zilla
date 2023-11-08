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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.EnumSet.allOf;
import static java.util.stream.Collectors.toList;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
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
    private static final String8FW HEADER_CONTENT_TYPE = new String8FW("content-type");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_PATH = new String8FW(":path");

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
        this.requests = createValidator == null ? null : createRequestTypes(createValidator);
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

    private List<HttpRequestType> createRequestTypes(
        BiFunction<ValidatorConfig, ToLongFunction<String>, Validator> createValidator)
    {
        List<HttpRequestType> result = new LinkedList<>();
        if (this.options != null && this.options.requests != null)
        {
            for (HttpRequestConfig request : this.options.requests)
            {
                String pattern = String.format("^%s/?(\\?(?<query0>.*))?$",
                    request.path.replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>.+?)"));
                Matcher pathMatcher = Pattern.compile(pattern).matcher("");
                Map<String8FW, Validator> headers = new HashMap<>();
                if (request.headers != null)
                {
                    for (HttpParamConfig header : request.headers)
                    {
                        headers.put(new String8FW(header.name), createValidator.apply(header.validator, this.resolveId));
                    }
                }
                Map<String, Validator> pathParams = new HashMap<>();
                if (request.pathParams != null)
                {
                    for (HttpParamConfig pathParam : request.pathParams)
                    {
                        pathParams.put(pathParam.name, createValidator.apply(pathParam.validator, this.resolveId));
                    }
                }
                Map<String, Validator> queryParams = new HashMap<>();
                if (request.queryParams != null)
                {
                    for (HttpParamConfig queryParam : request.queryParams)
                    {
                        queryParams.put(queryParam.name, createValidator.apply(queryParam.validator, this.resolveId));
                    }
                }
                Validator content = createValidator.apply(request.content, this.resolveId);
                HttpRequestType requestType = HttpRequestType.builder()
                    .path(request.path)
                    .pathMatcher(pathMatcher)
                    .method(request.method)
                    .contentType(request.contentType)
                    .headers(headers)
                    .pathParams(pathParams)
                    .queryParams(queryParams)
                    .content(content)
                    .build();
                result.add(requestType);
            }
        }
        return result;
    }

    public HttpRequestType resolveRequestType(
        HttpBeginExFW beginEx)
    {
        HttpRequestType result = null;
        if (requests != null && !requests.isEmpty())
        {
            String path = resolveHeaderValue(beginEx, HEADER_PATH);
            String method = resolveHeaderValue(beginEx, HEADER_METHOD);
            String contentType = resolveHeaderValue(beginEx, HEADER_CONTENT_TYPE);
            for (HttpRequestType request : requests)
            {
                if (matchMethod(request, method) && matchContentType(request, contentType) && matchPath(request, path))
                {
                    result = request;
                    break;
                }
            }
        }
        return result;
    }

    private boolean matchMethod(
        HttpRequestType request,
        String method)
    {
        return method == null || request.method == null || method.equals(request.method.name());
    }

    private boolean matchContentType(
        HttpRequestType request,
        String contentType)
    {
        return contentType == null || request.contentType == null || request.contentType.contains(contentType);
    }

    private boolean matchPath(
        HttpRequestType request,
        String path)
    {
        return request.pathMatcher.reset(path).matches();
    }

    public boolean validateHeaders(
        HttpRequestType request,
        HttpBeginExFW beginEx)
    {
        return request == null ||
            validateHeaderValues(request, beginEx) &&
            validatePathParams(request) &&
            validateQueryParams(request);
    }

    private boolean validateHeaderValues(
        HttpRequestType request,
        HttpBeginExFW beginEx)
    {
        MutableBoolean valid = new MutableBoolean(true);
        if (request != null && request.headers != null)
        {
            beginEx.headers().forEach(header ->
            {
                if (valid.value)
                {
                    Validator validator = request.headers.get(header.name());
                    if (validator != null)
                    {
                        String16FW value = header.value();
                        if (!validator.read(value.value(), value.offset(), value.length()))
                        {
                            valid.value = false;
                        }
                    }
                }
            });
        }
        return valid.value;
    }

    private Map<String, String8FW> parsePathParams(
        String pathTemplate,
        Matcher matcher)
    {
        Map<String, String8FW> result = new HashMap<>();
        String[] segments = pathTemplate.split("/");
        for (String segment : segments)
        {
            if (segment.startsWith("{") && segment.endsWith("}"))
            {
                String name = segment.substring(1, segment.length() - 1);
                String8FW value = new String8FW(URLDecoder.decode(matcher.group(name), UTF_8));
                result.put(name, value);
            }
        }
        return result;
    }

    private boolean validatePathParams(
        HttpRequestType request)
    {
        boolean valid = true;
        Map<String, String8FW> pathParams = parsePathParams(request.path, request.pathMatcher);
        for (String name : pathParams.keySet())
        {
            Validator validator = request.pathParams.get(name);
            if (validator != null)
            {
                String8FW value = pathParams.get(name);
                if (!validator.read(value.value(), value.offset(), value.length()))
                {
                    valid = false;
                    break;
                }
            }
        }
        return valid;
    }

    private Map<String, String8FW> parseQueryParams(
        String query)
    {
        Map<String, String8FW> queryParams = new HashMap<>();
        if (query != null && !query.isBlank())
        {
            int ampersandIndex = -1;
            do
            {
                int equalsIndex = query.indexOf('=', ampersandIndex + 1);
                int nextAmpersandIndex = query.indexOf('&', ampersandIndex + 1);
                if (equalsIndex != -1 && (equalsIndex < nextAmpersandIndex || nextAmpersandIndex == -1))
                {
                    String key = query.substring(ampersandIndex + 1, equalsIndex);
                    String value = nextAmpersandIndex == -1 ?
                        query.substring(equalsIndex + 1) :
                        query.substring(equalsIndex + 1, nextAmpersandIndex);
                    if (!key.isEmpty())
                    {
                        queryParams.put(URLDecoder.decode(key, UTF_8), new String8FW(URLDecoder.decode(value, UTF_8)));
                    }
                }
                ampersandIndex = nextAmpersandIndex;
            }
            while (ampersandIndex != -1);
        }
        return queryParams;
    }

    private boolean validateQueryParams(
        HttpRequestType request)
    {
        boolean valid = true;
        Map<String, String8FW> queryParams = parseQueryParams(request.pathMatcher.group("query0"));
        for (String name : queryParams.keySet())
        {
            Validator validator = request.queryParams.get(name);
            if (validator != null)
            {
                String8FW value = queryParams.get(name);
                if (!validator.read(value.value(), value.offset(), value.length()))
                {
                    valid = false;
                    break;
                }
            }
        }
        return valid;
    }

    public boolean validateContent(
        HttpRequestType request,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean valid = true;
        if (request != null && request.content != null)
        {
            Validator validator = request.content;
            valid = validator.read(buffer, index, length);
        }
        return valid;
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

    private static String resolveHeaderValue(
        HttpBeginExFW beginEx,
        String8FW headerName)
    {
        String result = null;
        HttpHeaderFW header = beginEx.headers().matchFirst(h -> headerName.equals(h.name()));
        if (header != null)
        {
            result = header.value().asString();
        }
        return result;
    }
}
