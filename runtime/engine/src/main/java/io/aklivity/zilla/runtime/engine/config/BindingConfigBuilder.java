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

public final class BindingConfigBuilder<T> extends ConfigBuilder<T, BindingConfigBuilder<T>>
{
    public static final List<RouteConfig> ROUTES_DEFAULT = emptyList();
    public static final List<NamespaceConfig> COMPOSITES_DEFAULT = emptyList();

    private final Function<BindingConfig, T> mapper;

    private String vault;
    private String namespace;
    private String name;
    private String type;
    private KindConfig kind;
    private String entry;
    private String exit;
    private OptionsConfig options;
    private List<RouteConfig> routes;
    private TelemetryRefConfig telemetryRef;
    private List<NamespaceConfig> composites;

    BindingConfigBuilder(
        Function<BindingConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<BindingConfigBuilder<T>> thisType()
    {
        return (Class<BindingConfigBuilder<T>>) getClass();
    }

    public BindingConfigBuilder<T> vault(
        String vault)
    {
        this.vault = vault;
        return this;
    }

    public BindingConfigBuilder<T> namespace(
        String namespace)
    {
        this.namespace = namespace;
        return this;
    }

    public BindingConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public BindingConfigBuilder<T> type(
        String type)
    {
        this.type = type;
        return this;
    }

    public BindingConfigBuilder<T> kind(
        KindConfig kind)
    {
        this.kind = kind;
        return this;
    }

    public BindingConfigBuilder<T> entry(
        String entry)
    {
        this.entry = entry;
        return this;
    }

    public BindingConfigBuilder<T> exit(
        String exit)
    {
        this.exit = exit;
        return this;
    }

    public <C extends ConfigBuilder<BindingConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, BindingConfigBuilder<T>>, C> options)
    {
        return options.apply(this::options);
    }

    public BindingConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    public RouteConfigBuilder<BindingConfigBuilder<T>> route()
    {
        return new RouteConfigBuilder<>(this::route)
            .order(routes != null ? routes.size() : 0);
    }

    public BindingConfigBuilder<T> route(
        RouteConfig route)
    {
        if (routes == null)
        {
            routes = new LinkedList<>();
        }

        assert route.order == routes.size();

        routes.add(route);
        return this;
    }

    public BindingConfigBuilder<T> routes(
        List<RouteConfig> routes)
    {
        routes.forEach(this::route);
        return this;
    }

    public TelemetryRefConfigBuilder<BindingConfigBuilder<T>> telemetry()
    {
        return new TelemetryRefConfigBuilder<>(this::telemetry);
    }

    public BindingConfigBuilder<T> telemetry(
        TelemetryRefConfig telemetryRef)
    {
        this.telemetryRef = telemetryRef;
        return this;
    }

    public NamespaceConfigBuilder<BindingConfigBuilder<T>> composite()
    {
        return new NamespaceConfigBuilder<>(this::composite);
    }

    public BindingConfigBuilder<T> composite(
        NamespaceConfig composite)
    {
        if (composites == null)
        {
            composites = new LinkedList<>();
        }
        composites.add(composite);
        return this;
    }

    public BindingConfigBuilder<T> composites(
        List<NamespaceConfig> composites)
    {
        composites.forEach(this::composite);
        return this;
    }

    @Override
    public T build()
    {
        if (exit != null)
        {
            route()
                .exit(exit)
                .build();
        }

        return mapper.apply(new BindingConfig(
            namespace,
            name,
            type,
            kind,
            entry,
            vault,
            options,
            Optional.ofNullable(routes).orElse(ROUTES_DEFAULT),
            telemetryRef,
            Optional.ofNullable(composites).orElse(COMPOSITES_DEFAULT)));
    }
}
