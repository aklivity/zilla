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
package io.aklivity.zilla.config.binding.a2a;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aClientPushConfigBuilder<T> extends ConfigBuilder<T, A2aClientPushConfigBuilder<T>>
{
    private final Function<A2aClientPushConfig, T> mapper;

    private List<String> allow;
    private int maxLength = -1;

    A2aClientPushConfigBuilder(
        Function<A2aClientPushConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aClientPushConfigBuilder<T>> thisType()
    {
        return (Class<A2aClientPushConfigBuilder<T>>) getClass();
    }

    public A2aClientPushConfigBuilder<T> allow(
        List<String> allow)
    {
        this.allow = allow;
        return this;
    }

    public A2aClientPushConfigBuilder<T> maxLength(
        int maxLength)
    {
        this.maxLength = maxLength;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aClientPushConfig(allow, maxLength));
    }
}
