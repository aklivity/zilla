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

public final class GenericStoreConfigBuilder<T> extends StoreConfigBuilder<T, GenericStoreConfigBuilder<T>>
{
    GenericStoreConfigBuilder(
        Function<StoreConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GenericStoreConfigBuilder<T>> thisType()
    {
        return (Class<GenericStoreConfigBuilder<T>>) getClass();
    }

    @Override
    public GenericStoreConfigBuilder<T> type(
        String type)
    {
        return super.type(type);
    }

    @Override
    public <C extends ConfigBuilder<GenericStoreConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, GenericStoreConfigBuilder<T>>, C> options)
    {
        return super.options(options);
    }

    @Override
    protected StoreConfig newStore(
        String namespace,
        String name,
        String type,
        OptionsConfig options)
    {
        return new GenericStoreConfig(namespace, name, type, options);
    }
}
