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
package io.aklivity.zilla.runtime.engine.test.internal.model.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public class TestModelConfigBuilder<T> extends ConfigBuilder<T, TestModelConfigBuilder<T>>
{
    private final Function<ModelConfig, T> mapper;

    private int length;
    private boolean read;
    private List<CatalogedConfig> catalogs;

    TestModelConfigBuilder(
        Function<ModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestModelConfigBuilder<T>> thisType()
    {
        return (Class<TestModelConfigBuilder<T>>) getClass();
    }

    public TestModelConfigBuilder<T> length(
        int length)
    {
        this.length = length;
        return this;
    }

    public TestModelConfigBuilder<T> read(
        boolean read)
    {
        this.read = read;
        return this;
    }

    public CatalogedConfigBuilder<TestModelConfigBuilder<T>> catalog()
    {
        return CatalogedConfig.builder(this::catalog);
    }

    public TestModelConfigBuilder<T> catalog(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestModelConfig(length, catalogs, read));
    }
}
