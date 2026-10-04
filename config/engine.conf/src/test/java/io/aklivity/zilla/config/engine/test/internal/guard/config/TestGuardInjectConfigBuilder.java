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
package io.aklivity.zilla.config.engine.test.internal.guard.config;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class TestGuardInjectConfigBuilder<T> extends ConfigBuilder<T, TestGuardInjectConfigBuilder<T>>
{
    private final Function<TestGuardInjectConfig, T> mapper;

    private String identity;
    private String credentials;

    TestGuardInjectConfigBuilder(
        Function<TestGuardInjectConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestGuardInjectConfigBuilder<T>> thisType()
    {
        return (Class<TestGuardInjectConfigBuilder<T>>) getClass();
    }

    public TestGuardInjectConfigBuilder<T> identity(
        String identity)
    {
        this.identity = identity;
        return this;
    }

    public TestGuardInjectConfigBuilder<T> credentials(
        String credentials)
    {
        this.credentials = credentials;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestGuardInjectConfig(identity, credentials));
    }
}
