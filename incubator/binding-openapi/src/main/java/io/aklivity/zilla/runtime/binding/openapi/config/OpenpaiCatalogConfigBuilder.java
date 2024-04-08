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

public final class OpenpaiCatalogConfigBuilder<T> extends ConfigBuilder<T, OpenpaiCatalogConfigBuilder<T>>
{
    private final Function<OpenapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;

    OpenpaiCatalogConfigBuilder(
        Function<OpenapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenpaiCatalogConfigBuilder<T>> thisType()
    {
        return (Class<OpenpaiCatalogConfigBuilder<T>>) getClass();
    }

    public OpenpaiCatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public OpenpaiCatalogConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public OpenpaiCatalogConfigBuilder<T> version(
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
