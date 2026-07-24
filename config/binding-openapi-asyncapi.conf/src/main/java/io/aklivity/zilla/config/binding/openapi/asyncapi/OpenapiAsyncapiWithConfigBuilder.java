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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class OpenapiAsyncapiWithConfigBuilder<T> extends ConfigBuilder<T, OpenapiAsyncapiWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String spec;
    private String operation;
    private String tag;

    OpenapiAsyncapiWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiAsyncapiWithConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiAsyncapiWithConfigBuilder<T>>) getClass();
    }

    public OpenapiAsyncapiWithConfigBuilder<T> spec(
        String spec)
    {
        this.spec = spec;
        return this;
    }

    public OpenapiAsyncapiWithConfigBuilder<T> operation(
        String operation)
    {
        this.operation = operation;
        return this;
    }

    public OpenapiAsyncapiWithConfigBuilder<T> tag(
        String tag)
    {
        this.tag = tag;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiAsyncapiWithConfig(spec, operation, tag));
    }
}
