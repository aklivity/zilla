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
package io.aklivity.zilla.runtime.engine.config;

import java.util.function.Function;

public class SchemaConfigBuilder<T> extends ConfigBuilder<T, SchemaConfigBuilder<T>>
{
    private final Function<SchemaConfig, T> mapper;

    private String schema;
    private String strategy;
    private String version;
    private int id;

    public SchemaConfigBuilder(
        Function<SchemaConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<SchemaConfigBuilder<T>> thisType()
    {
        return (Class<SchemaConfigBuilder<T>>) getClass();
    }

    public SchemaConfigBuilder<T> schema(
        String schema)
    {
        this.schema = schema;
        return this;
    }

    public SchemaConfigBuilder<T> strategy(
        String strategy)
    {
        this.strategy = strategy;
        return this;
    }

    public SchemaConfigBuilder<T> version(
        String version)
    {
        this.version = version;
        return this;
    }

    public SchemaConfigBuilder<T> id(
        int id)
    {
        this.id = id;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new SchemaConfig(schema, strategy, version, id));
    }
}
