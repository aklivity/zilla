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

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.OverlayConfigBuilder;

public final class A2aOpenapiCatalogConfigBuilder<T> extends ConfigBuilder<T, A2aOpenapiCatalogConfigBuilder<T>>
{
    private final Function<A2aOpenapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;
    private OverlayConfig overlay;

    A2aOpenapiCatalogConfigBuilder(
        Function<A2aOpenapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOpenapiCatalogConfigBuilder<T>> thisType()
    {
        return (Class<A2aOpenapiCatalogConfigBuilder<T>>) getClass();
    }

    public A2aOpenapiCatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public A2aOpenapiCatalogConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public A2aOpenapiCatalogConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public A2aOpenapiCatalogConfigBuilder<T> overlay(
        OverlayConfig overlay)
    {
        this.overlay = overlay;
        return this;
    }

    public OverlayConfigBuilder<A2aOpenapiCatalogConfigBuilder<T>> overlay()
    {
        return OverlayConfig.builder(this::overlay);
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aOpenapiCatalogConfig(name, subject, version, overlay));
    }
}
