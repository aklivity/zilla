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
package io.aklivity.zilla.runtime.catalog.inline.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class InlineSchemaConfigBuilder<T> extends ConfigBuilder<T, InlineSchemaConfigBuilder<T>>
{
    private final Function<InlineSchemaConfig, T> mapper;

    private String subject;
    private String version;
    private String schema;

    public InlineSchemaConfigBuilder(
        Function<InlineSchemaConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<InlineSchemaConfigBuilder<T>> thisType()
    {
        return (Class<InlineSchemaConfigBuilder<T>>) getClass();
    }

    public InlineSchemaConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public InlineSchemaConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public InlineSchemaConfigBuilder<T> schema(
        String schema)
    {
        this.schema = schema;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new InlineSchemaConfig(subject, version, schema));
    }
}
