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
package io.aklivity.zilla.config.binding.asyncapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class AsyncapiWithConfigBuilder<T> extends ConfigBuilder<T, AsyncapiWithConfigBuilder<T>>
{
    private final Function<WithConfig, T> mapper;

    private String spec;
    private String operation;

    AsyncapiWithConfigBuilder(
        Function<WithConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AsyncapiWithConfigBuilder<T>> thisType()
    {
        return (Class<AsyncapiWithConfigBuilder<T>>) getClass();
    }

    public AsyncapiWithConfigBuilder<T> spec(
        String spec)
    {
        this.spec = spec;
        return this;
    }

    public AsyncapiWithConfigBuilder<T> operation(
        String operation)
    {
        this.operation = operation;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new AsyncapiWithConfig(spec, operation));
    }
}
