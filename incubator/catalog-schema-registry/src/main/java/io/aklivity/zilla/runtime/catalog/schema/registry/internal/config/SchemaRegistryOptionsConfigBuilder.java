/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class SchemaRegistryOptionsConfigBuilder<T> extends ConfigBuilder<T, SchemaRegistryOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String url;
    private String context;

    SchemaRegistryOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SchemaRegistryOptionsConfigBuilder<T>> thisType()
    {
        return (Class<SchemaRegistryOptionsConfigBuilder<T>>) getClass();
    }

    public SchemaRegistryOptionsConfigBuilder<T> url(
        String url)
    {
        this.url = url;
        return this;
    }

    public SchemaRegistryOptionsConfigBuilder<T> context(
        String context)
    {
        this.context = context;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new SchemaRegistryOptionsConfig(url, context));
    }
}
