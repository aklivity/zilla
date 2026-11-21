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
package io.aklivity.zilla.config.binding.a2a.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aOpenapiSpecificationConfigBuilder<T> extends ConfigBuilder<T, A2aOpenapiSpecificationConfigBuilder<T>>
{
    private final Function<A2aOpenapiSpecificationConfig, T> mapper;

    private String label;
    private String server;
    private List<A2aOpenapiCatalogConfig> catalogs;
    private Map<String, String> security;

    A2aOpenapiSpecificationConfigBuilder(
        Function<A2aOpenapiSpecificationConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOpenapiSpecificationConfigBuilder<T>> thisType()
    {
        return (Class<A2aOpenapiSpecificationConfigBuilder<T>>) getClass();
    }

    public A2aOpenapiSpecificationConfigBuilder<T> label(
        String label)
    {
        this.label = label;
        return this;
    }

    public A2aOpenapiSpecificationConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public A2aOpenapiCatalogConfigBuilder<A2aOpenapiSpecificationConfigBuilder<T>> catalog()
    {
        return new A2aOpenapiCatalogConfigBuilder<>(this::catalog);
    }

    public A2aOpenapiSpecificationConfigBuilder<T> catalog(
        A2aOpenapiCatalogConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new ArrayList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    public A2aOpenapiSpecificationConfigBuilder<T> security(
        Map<String, String> security)
    {
        this.security = security;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aOpenapiSpecificationConfig(label, server, catalogs, security));
    }
}
