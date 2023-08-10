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

import static java.util.Collections.emptyMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class NamespaceRefConfigBuilder<T> implements ConfigBuilder<T>
{
    public static final Map<String, String> LINKS_DEFAULT = emptyMap();

    private final Function<NamespaceRefConfig, T> mapper;

    private String name;
    private Map<String, String> links;

    NamespaceRefConfigBuilder(
        Function<NamespaceRefConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public NamespaceRefConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public NamespaceRefConfigBuilder<T> link(
        String name,
        String value)
    {
        if (links == null)
        {
            links = new LinkedHashMap<>();
        }
        links.put(name, value);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new NamespaceRefConfig(
            name,
            Optional.ofNullable(links).orElse(LINKS_DEFAULT)));
    }

}
