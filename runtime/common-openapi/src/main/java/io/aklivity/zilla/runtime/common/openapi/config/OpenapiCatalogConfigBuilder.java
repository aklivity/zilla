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
package io.aklivity.zilla.runtime.common.openapi.config;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.OverlayConfigBuilder;

public final class OpenapiCatalogConfigBuilder<T>
{
    private final Function<OpenapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;
    private OverlayConfig overlay;

    OpenapiCatalogConfigBuilder(
        Function<OpenapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
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

    public OpenapiCatalogConfigBuilder<T> overlay(
        OverlayConfig overlay)
    {
        this.overlay = overlay;
        return this;
    }

    public OverlayConfigBuilder<OpenapiCatalogConfigBuilder<T>> overlay()
    {
        return OverlayConfig.builder(this::overlay);
    }

    public T build()
    {
        return mapper.apply(new OpenapiCatalogConfig(name, subject, version, overlay));
    }
}
