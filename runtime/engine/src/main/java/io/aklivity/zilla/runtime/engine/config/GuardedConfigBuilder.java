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

import static java.util.Collections.emptyList;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class GuardedConfigBuilder<T> extends ConfigBuilder<T, GuardedConfigBuilder<T>>
{
    public static final List<String> ROLES_DEFAULT = emptyList();

    private final Function<GuardedConfig, T> mapper;

    private String namespace;
    private String name;
    private List<String> roles;

    GuardedConfigBuilder(
        Function<GuardedConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GuardedConfigBuilder<T>> thisType()
    {
        return (Class<GuardedConfigBuilder<T>>) getClass();
    }

    public GuardedConfigBuilder<T> namespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
    }

    public GuardedConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public GuardedConfigBuilder<T> role(
        String role)
    {
        if (roles == null)
        {
            roles = new LinkedList<>();
        }
        roles.add(role);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GuardedConfig(
            namespace,
            name,
            Optional.ofNullable(roles).orElse(ROLES_DEFAULT)));
    }
}
