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

import java.time.Duration;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class HttpAccessControlConfigBuilder<T> extends ConfigBuilder<T, HttpAccessControlConfigBuilder<T>>
{
    private final Function<HttpAccessControlConfig, T> mapper;

    private HttpPolicyConfig policy;
    private HttpAllowConfig allow;
    private Duration maxAge;
    private HttpExposeConfig expose;

    HttpAccessControlConfigBuilder(
        Function<HttpAccessControlConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<HttpAccessControlConfigBuilder<T>> thisType()
    {
        return (Class<HttpAccessControlConfigBuilder<T>>) getClass();
    }

    public HttpAccessControlConfigBuilder<T> policy(
        HttpPolicyConfig policy)
    {
        this.policy = policy;
        return this;
    }

    public HttpAllowConfigBuilder<HttpAccessControlConfigBuilder<T>> allow()
    {
        return new HttpAllowConfigBuilder<>(this::allow);
    }

    public HttpAccessControlConfigBuilder<T> maxAge(
        Duration maxAge)
    {
        this.maxAge = maxAge;
        return this;
    }

    public HttpExposeConfigBuilder<HttpAccessControlConfigBuilder<T>> expose()
    {
        return new HttpExposeConfigBuilder<>(this::expose);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpAccessControlConfig(policy, allow, maxAge, expose));
    }

    private HttpAccessControlConfigBuilder<T> allow(
        HttpAllowConfig allow)
    {
        this.allow = allow;
        return this;
    }

    private HttpAccessControlConfigBuilder<T> expose(
        HttpExposeConfig expose)
    {
        this.expose = expose;
        return this;
    }
}
