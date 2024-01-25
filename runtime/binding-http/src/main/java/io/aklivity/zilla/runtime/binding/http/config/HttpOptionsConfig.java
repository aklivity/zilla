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
package io.aklivity.zilla.runtime.binding.http.config;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class HttpOptionsConfig extends OptionsConfig
{
    public final SortedSet<HttpVersion>  versions;
    public final Map<String8FW, String16FW>  overrides;
    public final HttpAccessControlConfig access;
    public final HttpAuthorizationConfig authorization;
    public final List<HttpRequestConfig> requests;

    public static HttpOptionsConfigBuilder<HttpOptionsConfig> builder()
    {
        return new HttpOptionsConfigBuilder<>(HttpOptionsConfig.class::cast);
    }

    public static <T> HttpOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new HttpOptionsConfigBuilder<>(mapper);
    }

    HttpOptionsConfig(
        SortedSet<HttpVersion>  versions,
        Map<String8FW, String16FW> overrides,
        HttpAccessControlConfig access,
        HttpAuthorizationConfig authorization,
        List<HttpRequestConfig> requests)
    {
        super(requests != null && !requests.isEmpty()
            ? requests.stream()
                .flatMap(request -> Stream.concat(
                    Stream.of(request.content),
                    Stream.concat(
                        request.headers != null
                            ? request.headers.stream().flatMap(header -> Stream.of(header != null ? header.model : null))
                            : Stream.empty(),
                        Stream.concat(
                            request.pathParams != null
                                ? request.pathParams.stream().flatMap(param -> Stream.of(param != null ? param.model : null))
                                : Stream.empty(),
                            request.queryParams != null
                                ? request.queryParams.stream().flatMap(param -> Stream.of(param != null ? param.model : null))
                                : Stream.empty()))).filter(Objects::nonNull))
                .collect(Collectors.toList())
            : emptyList());

        this.versions = versions;
        this.overrides = overrides;
        this.access = access;
        this.authorization = authorization;
        this.requests = requests;
    }
}
