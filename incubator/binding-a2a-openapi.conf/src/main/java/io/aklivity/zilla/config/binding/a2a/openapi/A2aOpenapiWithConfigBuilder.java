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
package io.aklivity.zilla.config.binding.a2a.openapi;

import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class A2aOpenapiWithConfigBuilder<T> extends ConfigBuilder<T, A2aOpenapiWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String spec;
    private String operation;
    private String tag;
    private Map<String, String> params;

    A2aOpenapiWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOpenapiWithConfigBuilder<T>> thisType()
    {
        return (Class<A2aOpenapiWithConfigBuilder<T>>) getClass();
    }

    public A2aOpenapiWithConfigBuilder<T> spec(
        String spec)
    {
        this.spec = spec;
        return this;
    }

    public A2aOpenapiWithConfigBuilder<T> operation(
        String operation)
    {
        this.operation = operation;
        return this;
    }

    public A2aOpenapiWithConfigBuilder<T> tag(
        String tag)
    {
        this.tag = tag;
        return this;
    }

    public A2aOpenapiWithConfigBuilder<T> params(
        Map<String, String> params)
    {
        this.params = params;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aOpenapiWithConfig(spec, operation, tag, params));
    }
}
