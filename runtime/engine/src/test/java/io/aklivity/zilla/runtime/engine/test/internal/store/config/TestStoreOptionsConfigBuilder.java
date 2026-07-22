/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.store.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestStoreOptionsConfigBuilder<T> extends ConfigBuilder<T, TestStoreOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private Map<String, String> entries;

    TestStoreOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestStoreOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestStoreOptionsConfigBuilder<T>>) getClass();
    }

    public TestStoreOptionsConfigBuilder<T> entry(
        String key,
        String value)
    {
        if (entries == null)
        {
            entries = new LinkedHashMap<>();
        }
        entries.put(key, value);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestStoreOptionsConfig(entries));
    }
}
