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

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public class BindingConfig
{
    public transient long id;
    public transient long entryId;
    public transient ToLongFunction<String> resolveId;

    public transient long vaultId;

    public transient long[] metricIds;

    public final String namespace;
    public final String name;
    public final String qname;
    public final String type;
    public final KindConfig kind;
    public final String entry;
    public final String vault;
    public final OptionsConfig options;
    public final Collection<CatalogedConfig> catalogs;
    public final List<RouteConfig> routes;
    public final TelemetryRefConfig telemetryRef;
    public final List<NamespaceConfig> composites;

    public static BindingConfigBuilder<BindingConfig> builder()
    {
        return new BindingConfigBuilder<>(identity());
    }

    public static <T> BindingConfigBuilder<T> builder(
        Function<BindingConfig, T> mapper)
    {
        return new BindingConfigBuilder<>(mapper);
    }

    public static BindingConfigBuilder<BindingConfig> builder(
        BindingConfig binding)
    {
        return builder()
            .vault(binding.vault)
            .namespace(binding.namespace)
            .name(binding.name)
            .type(binding.type)
            .kind(binding.kind)
            .entry(binding.entry)
            .options(binding.options)
            .catalogs(binding.catalogs)
            .routes(binding.routes)
            .telemetry(binding.telemetryRef)
            .composites(binding.composites);
    }

    BindingConfig(
        String namespace,
        String name,
        String type,
        KindConfig kind,
        String entry,
        String vault,
        OptionsConfig options,
        Collection<CatalogedConfig> catalogs,
        List<RouteConfig> routes,
        TelemetryRefConfig telemetryRef,
        List<NamespaceConfig> namespaces)
    {
        this.namespace = requireNonNull(namespace);
        this.name = requireNonNull(name);
        this.qname = String.format("%s:%s", namespace, name);
        this.type = requireNonNull(type);
        this.kind = requireNonNull(kind);
        this.entry = entry;
        this.vault = vault;
        this.options = options;
        this.routes = routes;
        this.catalogs = catalogs;
        this.telemetryRef = telemetryRef;
        this.composites = namespaces;
    }
}
