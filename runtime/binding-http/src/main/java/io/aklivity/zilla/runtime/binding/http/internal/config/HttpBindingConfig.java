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
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpParamConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpResponseConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpVersion;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;

public final class HttpBindingConfig
{
    private static final Function<Function<String, String>, String> DEFAULT_CREDENTIALS = f -> null;
    private static final SortedSet<HttpVersion> DEFAULT_VERSIONS = new TreeSet<>(allOf(HttpVersion.class));
    private static final HttpAccessControlConfig DEFAULT_ACCESS_CONTROL =
            HttpAccessControlConfig.builder().policy(SAME_ORIGIN).build();
    private static final String8FW HEADER_CONTENT_TYPE = new String8FW("content-type");
    private static final String8FW HEADER_METHOD = new String8FW(":method");
    private static final String8FW HEADER_PATH = new String8FW(":path");
    private static final String8FW HEADER_STATUS = new String8FW(":status");
    private static final HttpQueryStringComparator QUERY_STRING_COMPARATOR = new HttpQueryStringComparator();

    public final long id;
    public final String name;
    public final HttpOptionsConfig options;
    public final KindConfig kind;
    public final List<HttpRouteConfig> routes;
    public final ToLongFunction<String> resolveId;
    public final Function<Function<String, String>, String> credentials;
    public final List<HttpRequestType> requests;

    public HttpBindingConfig(
        BindingConfig binding,
        Function<ModelConfig, ValidatorHandler> supplyValidator)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = HttpOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(HttpRouteConfig::new).collect(toList());
        this.resolveId = binding.resolveId;
        this.credentials = options != null && options.authorization != null ?
                asAccessor(options.authorization.credentials) : DEFAULT_CREDENTIALS;
        this.requests = supplyValidator == null ? null : createRequestTypes(supplyValidator);
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
        Function<ModelConfig, ValidatorHandler> supplyValidator)
    {
        List<HttpRequestType> requestTypes = new LinkedList<>();
        if (this.options != null && this.options.requests != null)
        {
            for (HttpRequestConfig request : this.options.requests)
            {
                Map<String8FW, ValidatorHandler> headers = new HashMap<>();
                if (request.headers != null)
                {
                    for (HttpParamConfig header : request.headers)
                    {
                        headers.put(new String8FW(header.name), supplyValidator.apply(header.model));
                    }
                }

                Map<String, ValidatorHandler> pathParams = new Object2ObjectHashMap<>();
                if (request.pathParams != null)
                {
                    for (HttpParamConfig pathParam : request.pathParams)
                    {
                        pathParams.put(pathParam.name, supplyValidator.apply(pathParam.model));
                    }
                }

                Map<String, ValidatorHandler> queryParams = new TreeMap<>(QUERY_STRING_COMPARATOR);
                if (request.queryParams != null)
                {
                    for (HttpParamConfig queryParam : request.queryParams)
                    {
                        queryParams.put(queryParam.name, supplyValidator.apply(queryParam.model));
                    }
                }

                List<HttpRequestType.Response> responses = new LinkedList<>();
                if (request.responses != null)
                {
                    for (HttpResponseConfig response0 : request.responses)
                    {
                        Map<String8FW, ValidatorHandler> responseHeaderValidators = new HashMap<>();
                        if (response0.headers != null)
                        {
                            for (HttpParamConfig header : response0.headers)
                            {
                                String8FW name = new String8FW(header.name);
                                ValidatorHandler validator = supplyValidator.apply(header.model);
                                if (validator != null)
                                {
                                    responseHeaderValidators.put(name, validator);
                                }
                            }
                        }
                        HttpRequestType.Response response = new HttpRequestType.Response(response0.status, response0.contentType,
                            responseHeaderValidators, response0.content);
                        responses.add(response);
                    }
                }
                HttpRequestType requestType = HttpRequestType.builder()
                    .path(request.path)
                    .method(request.method)
                    .contentType(request.contentType)
                    .headers(headers)
                    .pathParams(pathParams)
                    .queryParams(queryParams)
                    .content(request.content)
                    .responses(responses)
                    .build();
                requestTypes.add(requestType);
            }
        }
        return requestTypes;
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
            for (HttpRequestType requestType : requests)
            {
                if (matchRequestMethod(requestType, method) &&
                    matchRequestContentType(requestType, contentType) &&
                    matchRequestPath(requestType, path))
                {
                    result = requestType;
                    break;
                }
            }
        }
        return result;
    }

    public HttpRequestType.Response resolveResponse(
        HttpRequestType requestType,
        HttpBeginExFW beginEx)
    {
        HttpRequestType.Response result = null;
        if (requestType != null && requestType.responses != null)
        {
            String status = resolveHeaderValue(beginEx, HEADER_STATUS);
            String contentType = resolveHeaderValue(beginEx, HEADER_CONTENT_TYPE);
            for (HttpRequestType.Response response : requestType.responses)
            {
                if (matchResponseStatus(response, status) &&
                    matchResponseContentType(response, contentType))
                {
                    result = response;
                }
            }
        }
        return result;
    }

    private boolean matchRequestMethod(
        HttpRequestType requestType,
        String method)
    {
        return method == null || requestType.method == null || method.equals(requestType.method.name());
    }

    private boolean matchRequestContentType(
        HttpRequestType requestType,
        String contentType)
    {
        return contentType == null || requestType.contentType == null || requestType.contentType.contains(contentType);
    }

    private boolean matchRequestPath(
        HttpRequestType requestType,
        String path)
    {
        return requestType.pathMatcher.reset(path).matches();
    }

    private boolean matchResponseStatus(
        HttpRequestType.Response response,
        String status)
    {
        return status == null || response.status == null || response.status.contains(status);
    }

    private boolean matchResponseContentType(
        HttpRequestType.Response response,
        String contentType)
    {
        return contentType == null || response.contentType == null || response.contentType.contains(contentType);
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
