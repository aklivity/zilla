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

public final class GenericGuardConfigBuilder<T> extends GuardConfigBuilder<T, GenericGuardConfigBuilder<T>>
{
    GenericGuardConfigBuilder(
        Function<GuardConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GenericGuardConfigBuilder<T>> thisType()
    {
        return (Class<GenericGuardConfigBuilder<T>>) getClass();
    }

    @Override
    public GenericGuardConfigBuilder<T> type(
        String type)
    {
        return super.type(type);
    }

    @Override
    public <C extends ConfigBuilder<GenericGuardConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, GenericGuardConfigBuilder<T>>, C> options)
    {
        return super.options(options);
    }

    @Override
    protected GuardConfig newGuard(
        String namespace,
        String name,
        String type,
        String kind,
        String store,
        OptionsConfig options)
    {
        return new GenericGuardConfig(namespace, name, type, kind, store, options);
    }
}
