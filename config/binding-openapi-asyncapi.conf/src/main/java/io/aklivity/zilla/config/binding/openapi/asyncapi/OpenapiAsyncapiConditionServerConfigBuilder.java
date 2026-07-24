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

public final class OpenapiAsyncapiConditionServerConfigBuilder<T>
    extends ConfigBuilder<T, OpenapiAsyncapiConditionServerConfigBuilder<T>>
{
    private final Function<OpenapiAsyncapiConditionServerConfig, T> mapper;

    private String url;

    OpenapiAsyncapiConditionServerConfigBuilder(
        Function<OpenapiAsyncapiConditionServerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiAsyncapiConditionServerConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiAsyncapiConditionServerConfigBuilder<T>>) getClass();
    }

    public OpenapiAsyncapiConditionServerConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiAsyncapiConditionServerConfig(url));
    }
}
