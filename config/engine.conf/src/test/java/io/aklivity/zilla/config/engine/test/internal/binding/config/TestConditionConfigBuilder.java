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
package io.aklivity.zilla.config.engine.test.internal.binding.config;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class TestConditionConfigBuilder<T> extends ConfigBuilder<T, TestConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String match;

    TestConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestConditionConfigBuilder<T>> thisType()
    {
        return (Class<TestConditionConfigBuilder<T>>) getClass();
    }

    public TestConditionConfigBuilder<T> match(
        String match)
    {
        this.match = match;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestConditionConfig(match));
    }
}
