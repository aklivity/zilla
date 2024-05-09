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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestBindingOptionsConfigBuilder<T> extends ConfigBuilder<T, TestBindingOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String mode;
    private TestAuthorizationConfig authorization;
    private List<CatalogedConfig> catalogs;
    private List<TestBindingOptionsConfig.Event> events;
    private List<TestBindingOptionsConfig.CatalogAssertion> catalogAssertions;

    TestBindingOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestBindingOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestBindingOptionsConfigBuilder<T>>) getClass();
    }

    public TestBindingOptionsConfigBuilder<T> mode(
        String mode)
    {
        this.mode = mode;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> catalog(
        List<CatalogedConfig> catalogs)
    {
        this.catalogs = catalogs;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> authorization(
        String name,
        String credentials)
    {
        this.authorization = new TestAuthorizationConfig(name, credentials);
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> event(
        long timestamp,
        String message)
    {
        if (this.events == null)
        {
            this.events = new LinkedList<>();
        }
        this.events.add(new TestBindingOptionsConfig.Event(timestamp, message));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> catalogAssertion(
        int id,
        String schema,
        long interval)
    {
        if (this.catalogAssertions == null)
        {
            this.catalogAssertions = new LinkedList<>();
        }
        this.catalogAssertions.add(new TestBindingOptionsConfig.CatalogAssertion(id, schema, interval));
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestBindingOptionsConfig(mode, authorization, catalogs, events, catalogAssertions));
    }
}
