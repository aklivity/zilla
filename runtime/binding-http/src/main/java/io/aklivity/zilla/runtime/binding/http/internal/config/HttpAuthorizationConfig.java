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

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpAuthorizationConfig
{
    public static final Function<Function<String, String>, String> DEFAULT_CREDENTIALS = f -> null;

    public final String name;
    public final HttpCredentialsConfig credentials;

    public HttpAuthorizationConfig(
        String name,
        HttpCredentialsConfig credentials)
    {
        this.name = name;
        this.credentials = credentials;
    }

    public Function<Function<String, String>, String> credentials()
    {
        return credentials != null ? credentials.accessor : DEFAULT_CREDENTIALS;
    }

    public static final class HttpCredentialsConfig
    {
        public final List<HttpPatternConfig> headers;
        public final List<HttpPatternConfig> parameters;
        public final List<HttpPatternConfig> cookies;

        private final Function<Function<String, String>, String> accessor;

        public HttpCredentialsConfig(
            List<HttpPatternConfig> headers)
        {
            this(headers, null, null);
        }

        public HttpCredentialsConfig(
            List<HttpPatternConfig> headers,
            List<HttpPatternConfig> parameters,
            List<HttpPatternConfig> cookies)
        {
            this.headers = headers;
            this.parameters = parameters;
            this.cookies = cookies;
            this.accessor = asAccessor(headers, parameters, cookies);
        }

        private Function<Function<String, String>, String> asAccessor(
            List<HttpPatternConfig> headers,
            List<HttpPatternConfig> parameters,
            List<HttpPatternConfig> cookies)
        {
            Function<Function<String, String>, String> accessor = DEFAULT_CREDENTIALS;

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
                                    config.pattern.replace("{credentials}", "(?<credentials>[^\\s]+)")))
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

    public static final class HttpPatternConfig
    {
        public final String name;
        public final String pattern;

        public HttpPatternConfig(
            String name,
            String pattern)
        {
            this.name = name;
            this.pattern = pattern;
        }
    }
}
