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

import java.util.Set;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;

public final class OpenapiAsyncapiSpecConfigBuilder<T> extends ConfigBuilder<T, OpenapiAsyncapiSpecConfigBuilder<T>>
{
    private final Function<OpenapiAsyncapiSpecConfig, T> mapper;

    private Set<OpenapiSpecificationConfig> openapi;
    private Set<AsyncapiSpecificationConfig> asyncapi;

    OpenapiAsyncapiSpecConfigBuilder(
        Function<OpenapiAsyncapiSpecConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiAsyncapiSpecConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiAsyncapiSpecConfigBuilder<T>>) getClass();
    }

    public OpenapiAsyncapiSpecConfigBuilder<T> openapi(
        Set<OpenapiSpecificationConfig> openapi)
    {
        this.openapi = openapi;
        return this;
    }

    public OpenapiAsyncapiSpecConfigBuilder<T> asyncapi(
        Set<AsyncapiSpecificationConfig> asyncapi)
    {
        this.asyncapi = asyncapi;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiAsyncapiSpecConfig(openapi, asyncapi));
    }
}
