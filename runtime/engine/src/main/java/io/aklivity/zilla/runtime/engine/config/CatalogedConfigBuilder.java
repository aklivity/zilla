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

import java.util.Optional;
import java.util.function.Function;

import org.agrona.collections.ObjectHashSet;

public final class CatalogedConfigBuilder<T> extends ConfigBuilder<T, CatalogedConfigBuilder<T>>
{
    private final Function<CatalogedConfig, T> mapper;

    private String name;
    private ObjectHashSet<SchemaConfig> schemas;

    CatalogedConfigBuilder(
        Function<CatalogedConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<CatalogedConfigBuilder<T>> thisType()
    {
        return (Class<CatalogedConfigBuilder<T>>) getClass();
    }

    public CatalogedConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public SchemaConfigBuilder<CatalogedConfigBuilder<T>> schema()
    {
        return new SchemaConfigBuilder<>(this::schema);
    }

    public CatalogedConfigBuilder<T> schema(
        SchemaConfig schema)
    {
        if (schemas == null)
        {
            schemas = new ObjectHashSet<>();
        }
        schemas.add(schema);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new CatalogedConfig(
            name,
            Optional.ofNullable(schemas).orElse(new ObjectHashSet<>()))
        );
    }
}
