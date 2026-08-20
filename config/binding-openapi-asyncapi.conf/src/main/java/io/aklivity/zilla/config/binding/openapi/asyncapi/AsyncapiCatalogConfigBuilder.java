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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import java.util.function.Function;

public final class AsyncapiCatalogConfigBuilder<T>
{
    private final Function<AsyncapiCatalogConfig, T> mapper;

    private String name;
    private String subject;
    private String version;
    private AsyncapiCatalogConfig overlay;

    AsyncapiCatalogConfigBuilder(
        Function<AsyncapiCatalogConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public AsyncapiCatalogConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public AsyncapiCatalogConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public AsyncapiCatalogConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public AsyncapiCatalogConfigBuilder<T> overlay(
        AsyncapiCatalogConfig overlay)
    {
        this.overlay = overlay;
        return this;
    }

    public AsyncapiCatalogConfigBuilder<AsyncapiCatalogConfigBuilder<T>> overlay()
    {
        return new AsyncapiCatalogConfigBuilder<>(this::overlay);
    }

    public T build()
    {
        return mapper.apply(new AsyncapiCatalogConfig(name, subject, version, overlay));
    }
}
