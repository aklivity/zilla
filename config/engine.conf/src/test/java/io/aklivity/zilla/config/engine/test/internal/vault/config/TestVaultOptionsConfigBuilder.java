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
package io.aklivity.zilla.config.engine.test.internal.vault.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestVaultOptionsConfigBuilder<T> extends ConfigBuilder<T, TestVaultOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<TestVaultEntryConfig> keys;
    private TestVaultEntryConfig signer;
    private List<TestVaultEntryConfig> trust;
    private List<TestVaultEntryConfig> wrap;

    TestVaultOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestVaultOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestVaultOptionsConfigBuilder<T>>) getClass();
    }

    public TestVaultOptionsConfigBuilder<T> key(
        String alias,
        String entry)
    {
        if (keys == null)
        {
            keys = new ArrayList<>();
        }
        keys.add(new TestVaultEntryConfig(alias, entry));
        return this;
    }

    public TestVaultOptionsConfigBuilder<T> signer(
        String alias,
        String entry)
    {
        signer = new TestVaultEntryConfig(alias, entry);
        return this;
    }

    public TestVaultOptionsConfigBuilder<T> trust(
        String alias,
        String entry)
    {
        if (trust == null)
        {
            trust = new ArrayList<>();
        }
        trust.add(new TestVaultEntryConfig(alias, entry));
        return this;
    }

    public TestVaultOptionsConfigBuilder<T> wrap(
        String alias,
        String entry)
    {
        if (wrap == null)
        {
            wrap = new ArrayList<>();
        }
        wrap.add(new TestVaultEntryConfig(alias, entry));
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TestVaultOptionsConfig(keys, signer, trust, wrap));
    }
}
