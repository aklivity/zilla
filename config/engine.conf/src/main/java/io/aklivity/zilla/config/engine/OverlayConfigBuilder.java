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

public final class OverlayConfigBuilder<T> extends ConfigBuilder<T, OverlayConfigBuilder<T>>
{
    private final Function<OverlayConfig, T> mapper;

    private String name;
    private String subject;
    private String version;
    private SchemaConfig schema;

    OverlayConfigBuilder(
        Function<OverlayConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OverlayConfigBuilder<T>> thisType()
    {
        return (Class<OverlayConfigBuilder<T>>) getClass();
    }

    public OverlayConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public OverlayConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public OverlayConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public SchemaConfigBuilder<OverlayConfigBuilder<T>> schema()
    {
        return new SchemaConfigBuilder<>(this::schema);
    }

    public OverlayConfigBuilder<T> schema(
        SchemaConfig schema)
    {
        this.schema = schema;
        return this;
    }

    @Override
    public T build()
    {
        SchemaConfig resolved = schema != null
            ? schema
            : SchemaConfig.builder().subject(subject).version(version).build();

        return mapper.apply(new OverlayConfig(name, resolved));
    }
}
