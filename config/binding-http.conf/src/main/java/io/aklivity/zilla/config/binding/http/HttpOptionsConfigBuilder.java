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
package io.aklivity.zilla.config.binding.http;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class HttpOptionsConfigBuilder<T> extends ConfigBuilder<T, HttpOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private SortedSet<HttpVersion>  versions;
    private Map<String, String>  overrides;
    private HttpAccessControlConfig access;
    private HttpAuthorizationConfig authorization;
    private List<HttpRequestConfig> requests;

    HttpOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpOptionsConfigBuilder<T>> thisType()
    {
        return (Class<HttpOptionsConfigBuilder<T>>) getClass();
    }

    public HttpOptionsConfigBuilder<T> version(
        HttpVersion version)
    {
        if (versions == null)
        {
            versions = new TreeSet<>();
        }
        versions.add(version);
        return this;
    }

    public HttpOptionsConfigBuilder<T> override(
        String name,
        String value)
    {
        if (overrides == null)
        {
            overrides = new LinkedHashMap<>();
        }
        overrides.put(name, value);
        return this;
    }

    public HttpOptionsConfigBuilder<T> requests(
        List<HttpRequestConfig> requests)
    {
        if (requests == null)
        {
            requests = new LinkedList<>();
        }
        this.requests = requests;
        return this;
    }

    public HttpOptionsConfigBuilder<T> request(
        HttpRequestConfig request)
    {
        if (this.requests == null)
        {
            this.requests = new LinkedList<>();
        }
        this.requests.add(request);
        return this;
    }

    public HttpRequestConfigBuilder<HttpOptionsConfigBuilder<T>> request()
    {
        return new HttpRequestConfigBuilder<>(this::request);
    }

    public HttpOptionsConfigBuilder<T> authorization(
        HttpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public HttpAuthorizationConfigBuilder<HttpOptionsConfigBuilder<T>> authorization()
    {
        return new HttpAuthorizationConfigBuilder<>(this::authorization);
    }

    private HttpOptionsConfigBuilder<T> access(
        HttpAccessControlConfig access)
    {
        this.access = access;
        return this;
    }

    public HttpAccessControlConfigBuilder<HttpOptionsConfigBuilder<T>> access()
    {
        return new HttpAccessControlConfigBuilder<>(this::access);
    }

    @Override
    public T build()
    {
        List<ModelConfig> models = resolveModels(requests);
        return mapper.apply(new HttpOptionsConfig(versions, overrides, access, authorization, requests, models, refs(requests)));
    }

    private static List<ModelConfig> resolveModels(
        List<HttpRequestConfig> requests)
    {
        return requests != null && !requests.isEmpty()
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
                        Stream.concat(
                            request.queryParams != null
                                ? request.queryParams.stream().flatMap(param -> Stream.of(param != null ? param.model : null))
                                : Stream.empty(),
                            Stream.concat(request.responses != null
                                ? request.responses.stream().flatMap(param -> Stream.of(param != null
                                ? param.content
                                : null))
                                : Stream.empty(), request.responses != null
                                ? request.responses.stream()
                                .flatMap(response -> response.headers != null
                                    ? response.headers.stream()
                                    .flatMap(param -> Stream.of(param != null ? param.model : null))
                                    : Stream.empty())
                                : Stream.empty())
                        )))).filter(Objects::nonNull))
            .collect(Collectors.toList())
            : emptyList();
    }

    private static List<NamedConfig> refs(
        List<HttpRequestConfig> requests)
    {
        List<NamedConfig> refs = new ArrayList<>();
        if (requests != null)
        {
            for (HttpRequestConfig request : requests)
            {
                refs.addAll(request.refs());
            }
        }
        return refs;
    }
}
