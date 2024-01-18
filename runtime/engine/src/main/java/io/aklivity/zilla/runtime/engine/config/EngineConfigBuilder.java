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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public final class EngineConfigBuilder<T> extends ConfigBuilder<T, EngineConfigBuilder<T>>
{
    private final Function<EngineConfig, T> mapper;

    private List<NamespaceConfig> namespaces;

    EngineConfigBuilder(
        Function<EngineConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<EngineConfigBuilder<T>> thisType()
    {
        return (Class<EngineConfigBuilder<T>>) getClass();
    }

    public NamespaceConfigBuilder<EngineConfigBuilder<T>> namespace()
    {
        return new NamespaceConfigBuilder<>(this::namespace);
    }

    public EngineConfigBuilder<T> namespace(
        NamespaceConfig namespace)
    {
        if (namespaces == null)
        {
            namespaces = new LinkedList<>();
        }
        namespaces.add(namespace);
        return this;
    }

    public EngineConfigBuilder<T> namespaces(
        List<NamespaceConfig> namespaces)
    {
        this.namespaces = namespaces;
        return this;
    }

    public T build()
    {
        if (namespaces == null)
        {
            namespaces = new LinkedList<>();
        }

        return mapper.apply(new EngineConfig(
            namespaces));
    }
}
