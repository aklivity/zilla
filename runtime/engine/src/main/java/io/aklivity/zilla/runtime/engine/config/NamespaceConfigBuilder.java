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

public class NamespaceConfigBuilder<T>
{
    public static final List<NamespaceRefConfig> NAMESPACES_DEFAULT = emptyList();
    public static final List<BindingConfig> BINDINGS_DEFAULT = emptyList();
    public static final List<GuardConfig> GUARDS_DEFAULT = emptyList();
    public static final List<VaultConfig> VAULTS_DEFAULT = emptyList();
    public static final TelemetryConfig TELEMETRY_DEFAULT = TelemetryConfig.EMPTY;

    private final Function<NamespaceConfig, T> mapper;

    private String name;
    private List<NamespaceRefConfig> namespaces;
    private TelemetryConfig telemetry;
    private List<BindingConfig> bindings;
    private List<GuardConfig> guards;
    private List<VaultConfig> vaults;

    NamespaceConfigBuilder(
        Function<NamespaceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public NamespaceConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public NamespaceRefConfigBuilder<NamespaceConfigBuilder<T>> namespace()
    {
        return new NamespaceRefConfigBuilder<>(this::namespace);
    }

    public NamespaceConfigBuilder<T> namespace(
        NamespaceRefConfig namespace)
    {
        if (namespaces == null)
        {
            namespaces = new LinkedList<>();
        }
        namespaces.add(namespace);
        return this;
    }

    public TelemetryConfigBuilder<NamespaceConfigBuilder<T>> telemetry()
    {
        return new TelemetryConfigBuilder<>(this::telemetry);
    }

    public NamespaceConfigBuilder<T> telemetry(
        TelemetryConfig telemetry)
    {
        this.telemetry = telemetry;
        return this;
    }

    public BindingConfigBuilder<NamespaceConfigBuilder<T>> binding()
    {
        return new BindingConfigBuilder<>(this::binding);
    }

    public NamespaceConfigBuilder<T> binding(
        BindingConfig binding)
    {
        if (bindings == null)
        {
            bindings = new LinkedList<>();
        }
        bindings.add(binding);
        return this;
    }

    public NamespaceConfigBuilder<T> bindings(
        List<BindingConfig> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public GuardConfigBuilder<NamespaceConfigBuilder<T>> guard()
    {
        return new GuardConfigBuilder<>(this::guard);
    }

    public NamespaceConfigBuilder<T> guard(
        GuardConfig guard)
    {
        if (guards == null)
        {
            guards = new LinkedList<>();
        }
        guards.add(guard);
        return this;
    }

    public NamespaceConfigBuilder<T> guards(
        List<GuardConfig> guards)
    {
        this.guards = guards;
        return this;
    }

    public VaultConfigBuilder<NamespaceConfigBuilder<T>> vault()
    {
        return new VaultConfigBuilder<>(this::vault);
    }

    public NamespaceConfigBuilder<T> vault(
        VaultConfig vault)
    {
        if (vaults == null)
        {
            vaults = new LinkedList<>();
        }
        vaults.add(vault);
        return this;
    }

    public NamespaceConfigBuilder<T> vaults(
        List<VaultConfig> vaults)
    {
        this.vaults = vaults;
        return this;
    }

    public T build()
    {
        return mapper.apply(new NamespaceConfig(
            name,
            Optional.ofNullable(namespaces).orElse(NAMESPACES_DEFAULT),
            Optional.ofNullable(telemetry).orElse(TELEMETRY_DEFAULT),
            Optional.ofNullable(bindings).orElse(BINDINGS_DEFAULT),
            Optional.ofNullable(guards).orElse(GUARDS_DEFAULT),
            Optional.ofNullable(vaults).orElse(VAULTS_DEFAULT)));
    }
}
