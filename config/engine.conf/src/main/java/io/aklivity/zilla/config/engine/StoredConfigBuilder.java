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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class StoredConfigBuilder<T> extends ConfigBuilder.Extensible<T, StoredConfigBuilder<T>>
{
    private final Function<StoredConfig, T> mapper;

    private String name;

    StoredConfigBuilder(
        Function<StoredConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<StoredConfigBuilder<T>> thisType()
    {
        return (Class<StoredConfigBuilder<T>>) getClass();
    }

    public StoredConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new StoredConfig(name, extensions()));
    }
}
