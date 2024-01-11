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
import io.aklivity.zilla.runtime.engine.converter.Converter;

public final class HttpRequestType
{
    private static final String PATH_FORMAT = "^%s/?(?:\\?.*)?$";
    private static final String PATH_REGEX = "\\{([a-zA-Z0-9_-]+)\\}";
    private static final String PATH_REPLACEMENT = "(?<$1>.+?)";
    private static final String QUERY_REGEX = "(?<=[?&])([^&=]+)=([^&]+)(?=&|$)";
    private static final Pattern QUERY_PATTERN = Pattern.compile(QUERY_REGEX);
    private static final String EMPTY_INPUT = "";

    // selectors
    public final String path;
    public final HttpRequestConfig.Method method;
    public final List<String> contentType;

    // matchers
    public final Matcher pathMatcher;
    public final Matcher queryMatcher;

    // validators
    public final Map<String8FW, Converter> headers;
    public final Map<String, Converter> pathParams;
    public final Map<String, Converter> queryParams;
    public final Converter content;

    private HttpRequestType(
        String path,
        HttpRequestConfig.Method method,
        List<String> contentType,
        Matcher pathMatcher,
        Matcher queryMatcher,
        Map<String8FW, Converter> headers,
        Map<String, Converter> pathParams,
        Map<String, Converter> queryParams,
        Converter content)
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
        private Map<String8FW, Converter> headers;
        private Map<String, Converter> pathParams;
        private Map<String, Converter> queryParams;
        private Converter content;

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
            Map<String8FW, Converter> headers)
        {
            this.headers = headers;
            return this;
        }

        public Builder pathParams(
            Map<String, Converter> pathParams)
        {
            this.pathParams = pathParams;
            return this;
        }

        public Builder queryParams(
            Map<String, Converter> queryParams)
        {
            this.queryParams = queryParams;
            return this;
        }

        public Builder content(
            Converter content)
        {
            this.content = content;
            return this;
        }

        public HttpRequestType build()
        {
            String pathPattern = String.format(PATH_FORMAT, path.replaceAll(PATH_REGEX, PATH_REPLACEMENT));
            Matcher pathMatcher = Pattern.compile(pathPattern).matcher(EMPTY_INPUT);
            Matcher queryMatcher = QUERY_PATTERN.matcher(EMPTY_INPUT);
            return new HttpRequestType(path, method, contentType, pathMatcher, queryMatcher, headers, pathParams, queryParams,
                content);
        }
    }
}
