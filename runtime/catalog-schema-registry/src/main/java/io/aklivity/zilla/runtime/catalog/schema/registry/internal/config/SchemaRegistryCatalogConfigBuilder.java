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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class SchemaRegistryCatalogConfigBuilder<T> extends ConfigBuilder<T, SchemaRegistryCatalogConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String host;
    private String port;
    private String context;

    SchemaRegistryCatalogConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SchemaRegistryCatalogConfigBuilder<T>> thisType()
    {
        return (Class<SchemaRegistryCatalogConfigBuilder<T>>) getClass();
    }

    public SchemaRegistryCatalogConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public SchemaRegistryCatalogConfigBuilder<T> port(
        String port)
    {
        this.port = port;
        return this;
    }

    public SchemaRegistryCatalogConfigBuilder<T> context(
        String context)
    {
        this.context = context;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new SchemaRegistryCatalogConfig(host, port, context));
    }
}
