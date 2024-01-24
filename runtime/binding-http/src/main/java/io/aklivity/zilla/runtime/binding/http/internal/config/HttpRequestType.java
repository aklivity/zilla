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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public final class HttpRequestType
{
    private static final String PATH_FORMAT = "^%s/?(?:\\?.*)?$";
    private static final String PATH_REGEX = "\\{([a-zA-Z0-9_-]+)\\}";
    private static final String PATH_REPLACEMENT = "(?<$1>.+?)";
    private static final String QUERY_REGEX = "(?<=[?&])([^&=]+)=([^&]+)(?=&|$)";
    private static final Pattern QUERY_PATTERN = Pattern.compile(QUERY_REGEX);
    private static final String EMPTY_INPUT = "";

    // request selectors
    public final String path;
    public final HttpRequestConfig.Method method;
    public final List<String> contentType;

    // request matchers
    public final Matcher pathMatcher;
    public final Matcher queryMatcher;

    // request validators
    public final Map<String8FW, Validator> headers;
    public final Map<String, Validator> pathParams;
    public final Map<String, Validator> queryParams;
    public final Validator content;

    // responses
    public final List<Response> responses;

    private HttpRequestType(
        String path,
        HttpRequestConfig.Method method,
        List<String> contentType,
        Matcher pathMatcher,
        Matcher queryMatcher,
        Map<String8FW, Validator> headers,
        Map<String, Validator> pathParams,
        Map<String, Validator> queryParams,
        Validator content,
        List<Response> responses)
    {
        this.path = path;
        this.method = method;
        this.contentType = contentType;
        this.pathMatcher = pathMatcher;
        this.queryMatcher = queryMatcher;
        this.headers = headers;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.content = content;
        this.responses = responses;
    }

    public static final class Response
    {
        public final List<String> status;
        public final List<String> contentType;
        public final Map<String8FW, Validator> headers;
        public final Validator content;

        public Response(
            List<String> status,
            List<String> contentType,
            Map<String8FW, Validator> headers,
            Validator content)
        {
            this.status = status;
            this.contentType = contentType;
            this.headers = headers;
            this.content = content;
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private String path;
        private HttpRequestConfig.Method method;
        private List<String> contentType;
        private Map<String8FW, Validator> headers;
        private Map<String, Validator> pathParams;
        private Map<String, Validator> queryParams;
        private Validator content;
        private List<Response> responses;

        public Builder path(
            String path)
        {
            this.path = path;
            return this;
        }

        public Builder method(
            HttpRequestConfig.Method method)
        {
            this.method = method;
            return this;
        }

        public Builder contentType(
            List<String> contentType)
        {
            this.contentType = contentType;
            return this;
        }

        public Builder headers(
            Map<String8FW, Validator> headers)
        {
            this.headers = headers;
            return this;
        }

        public Builder pathParams(
            Map<String, Validator> pathParams)
        {
            this.pathParams = pathParams;
            return this;
        }

        public Builder queryParams(
            Map<String, Validator> queryParams)
        {
            this.queryParams = queryParams;
            return this;
        }

        public Builder content(
            Validator content)
        {
            this.content = content;
            return this;
        }

        public Builder responses(
            List<Response> responses)
        {
            this.responses = responses;
            return this;
        }

        public HttpRequestType build()
        {
            String pathPattern = String.format(PATH_FORMAT, path.replaceAll(PATH_REGEX, PATH_REPLACEMENT));
            Matcher pathMatcher = Pattern.compile(pathPattern).matcher(EMPTY_INPUT);
            Matcher queryMatcher = QUERY_PATTERN.matcher(EMPTY_INPUT);
            return new HttpRequestType(path, method, contentType, pathMatcher, queryMatcher, headers, pathParams, queryParams,
                content, responses);
        }
    }
}
