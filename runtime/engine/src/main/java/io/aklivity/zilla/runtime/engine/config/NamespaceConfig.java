/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.LinkedList;
import java.util.List;

public class NamespaceConfig
{
    public static final String FILESYSTEM = "filesystem";

    public transient int id;
    public transient int configAt;

    public final String name;
    public final TelemetryConfig telemetry;
    public final List<BindingConfig> bindings;
    public final List<GuardConfig> guards;
    public final List<VaultConfig> vaults;
    public final List<CatalogConfig> catalogs;
    public final List<String> resources;

    public static NamespaceConfigBuilder<NamespaceConfig> builder()
    {
        return new NamespaceConfigBuilder<>(identity());
    }

    NamespaceConfig(
        String name,
        TelemetryConfig telemetry,
        List<BindingConfig> bindings,
        List<GuardConfig> guards,
        List<VaultConfig> vaults,
        List<CatalogConfig> catalogs)
    {
        this.name = requireNonNull(name);
        this.telemetry = telemetry;
        this.bindings = requireNonNull(bindings);
        this.guards = requireNonNull(guards);
        this.vaults = requireNonNull(vaults);
        this.catalogs = requireNonNull(catalogs);
        this.resources = resolveResources(this, telemetry, bindings, guards, vaults, catalogs);
    }

    private static List<String> resolveResources(
        NamespaceConfig namespace,
        TelemetryConfig telemetry,
        List<BindingConfig> bindings,
        List<GuardConfig> guards,
        List<VaultConfig> vaults,
        List<CatalogConfig> catalogs)
    {
        List<OptionsConfig> options = new LinkedList<>();

        if (telemetry != null && telemetry.exporters != null)
        {
            telemetry.exporters.stream()
                .filter(e -> e.options != null)
                .map(e -> e.options)
                .forEach(options::add);
        }

        bindings.stream()
            .filter(b -> b.options != null)
            .map(b -> b.options)
            .forEach(options::add);

        guards.stream()
            .filter(g -> g.options != null)
            .map(g -> g.options)
            .forEach(options::add);

        vaults.stream()
            .filter(v -> v.options != null)
            .map(v -> v.options)
            .forEach(options::add);

        catalogs.stream()
            .filter(c -> c.options != null)
            .map(c -> c.options)
            .forEach(options::add);

        return options.stream()
            .filter(o -> o.resources != null)
            .flatMap(o -> o.resources.stream())
            .sorted()
            .distinct()
            .toList();
    }
}
