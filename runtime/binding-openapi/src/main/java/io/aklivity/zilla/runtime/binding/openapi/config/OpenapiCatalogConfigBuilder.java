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
package io.aklivity.zilla.runtime.binding.openapi.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class OpenapiCatalogConfigBuilder<T> extends ConfigBuilder<T, OpenapiCatalogConfigBuilder<T>>
{
    private final Function<OpenapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;

    OpenapiCatalogConfigBuilder(
        Function<OpenapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiCatalogConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiCatalogConfigBuilder<T>>) getClass();
    }

    public OpenapiCatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public OpenapiCatalogConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public OpenapiCatalogConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiCatalogConfig(name, subject, version));
    }
}
