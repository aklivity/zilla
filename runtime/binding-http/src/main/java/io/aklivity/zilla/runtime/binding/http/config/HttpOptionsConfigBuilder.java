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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpOptionsConfigBuilder<T> implements ConfigBuilder<T>
{
    private final Function<HttpOptionsConfig, T> mapper;

    private SortedSet<HttpVersion>  versions;
    private Map<String8FW, String16FW>  overrides;
    private HttpAccessControlConfig access;
    private HttpAuthorizationConfig authorization;

    HttpOptionsConfigBuilder(
        Function<HttpOptionsConfig, T> mapper)
    {
        this.mapper = mapper;
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
        String8FW name,
        String16FW value)
    {
        if (overrides == null)
        {
            overrides = new LinkedHashMap<>();
        }
        overrides.put(name, value);
        return this;
    }

    public HttpAccessControlConfigBuilder<HttpOptionsConfigBuilder<T>> access()
    {
        return new HttpAccessControlConfigBuilder<>(this::access);
    }

    public HttpAuthorizationConfigBuilder<HttpOptionsConfigBuilder<T>> authorization()
    {
        return new HttpAuthorizationConfigBuilder<>(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpOptionsConfig(versions, overrides, access, authorization));
    }

    private HttpOptionsConfigBuilder<T> authorization(
        HttpAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    private HttpOptionsConfigBuilder<T> access(
        HttpAccessControlConfig access)
    {
        this.access = access;
        return this;
    }
}
