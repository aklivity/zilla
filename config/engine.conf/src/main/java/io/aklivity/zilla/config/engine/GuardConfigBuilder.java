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

public abstract class GuardConfigBuilder<T, B extends GuardConfigBuilder<T, B>> extends ConfigBuilder<T, B>
{
    private final Function<GuardConfig, T> mapper;

    private String namespace;
    private String name;
    private String type;
    private String kind;
    private String store;
    private OptionsConfig options;

    protected GuardConfigBuilder(
        Function<GuardConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public B namespace(
        String namespace)
    {
        this.namespace = namespace;
        return thisType().cast(this);
    }

    public B name(
        String name)
    {
        this.name = name;
        return thisType().cast(this);
    }

    public B type(
        String type)
    {
        this.type = type;
        return thisType().cast(this);
    }

    public B kind(
        String kind)
    {
        this.kind = kind;
        return thisType().cast(this);
    }

    public B store(
        String store)
    {
        this.store = store;
        return thisType().cast(this);
    }

    public <C extends ConfigBuilder<B, C>> C options(
        Function<Function<OptionsConfig, B>, C> options)
    {
        return options.apply(this::options);
    }

    public B options(
        OptionsConfig options)
    {
        this.options = options;
        return thisType().cast(this);
    }

    @Override
    public T build()
    {
        return mapper.apply(newGuard(namespace, name, type, kind, store, options));
    }

    protected abstract GuardConfig newGuard(
        String namespace,
        String name,
        String type,
        String kind,
        String store,
        OptionsConfig options);
}
