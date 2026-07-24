/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine;

import static java.util.Collections.emptyList;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class BindingConfigBuilder<T, B extends BindingConfigBuilder<T, B>> extends ConfigBuilder<T, B>
{
    public static final List<RouteConfig> ROUTES_DEFAULT = emptyList();
    public static final List<CatalogedConfig> CATALOGS_DEFAULT = emptyList();
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
    private List<CatalogedConfig> catalogs;
    private TelemetryRefConfig telemetryRef;

    protected BindingConfigBuilder(
        Function<BindingConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public B vault(
        String vault)
    {
        this.vault = vault;
        return thisType().cast(this);
    }

    public B namespace(
        String namespace)
    {
        this.namespace = namespace;
        return thisType().cast(this);
    }

    public B name(
        String name)
    {
        this.name = name;
        return thisType().cast(this);
    }

    public B type(
        String type)
    {
        this.type = type;
        return thisType().cast(this);
    }

    public B kind(
        KindConfig kind)
    {
        this.kind = kind;
        return thisType().cast(this);
    }

    public B entry(
        String entry)
    {
        this.entry = entry;
        return thisType().cast(this);
    }

    public B exit(
        String exit)
    {
        this.exit = exit;
        return thisType().cast(this);
    }

    public <C extends ConfigBuilder<B, C>> C options(
        Function<Function<OptionsConfig, B>, C> options)
    {
        return options.apply(this::options);
    }

    public B options(
        OptionsConfig options)
    {
        this.options = options;
        return thisType().cast(this);
    }

    public B catalogs(
        List<CatalogedConfig> catalogs)
    {
        this.catalogs = catalogs;
        return thisType().cast(this);
    }

    public CatalogedConfigBuilder<B> catalog()
    {
        return new CatalogedConfigBuilder<>(this::catalog);
    }

    public B catalog(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }

        catalogs.add(catalog);
        return thisType().cast(this);
    }

    public RouteConfigBuilder<B, ?> route()
    {
        return new GenericRouteConfigBuilder<>(this::route)
            .order(nextRouteOrder());
    }

    protected final int nextRouteOrder()
    {
        return routes != null ? routes.size() : 0;
    }

    public B route(
        RouteConfig route)
    {
        if (routes == null)
        {
            routes = new LinkedList<>();
        }

        assert route.order == routes.size();

        routes.add(route);
        return thisType().cast(this);
    }

    public B routes(
        List<RouteConfig> routes)
    {
        routes.forEach(this::route);
        return thisType().cast(this);
    }

    public TelemetryRefConfigBuilder<B> telemetry()
    {
        return new TelemetryRefConfigBuilder<>(this::telemetry);
    }

    public B telemetry(
        TelemetryRefConfig telemetryRef)
    {
        this.telemetryRef = telemetryRef;
        return thisType().cast(this);
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

        return mapper.apply(newBinding(
            namespace,
            name,
            type,
            kind,
            entry,
            vault,
            options,
            Optional.ofNullable(catalogs).orElse(CATALOGS_DEFAULT),
            Optional.ofNullable(routes).orElse(ROUTES_DEFAULT),
            telemetryRef));
    }

    protected abstract BindingConfig newBinding(
        String namespace,
        String name,
        String type,
        KindConfig kind,
        String entry,
        String vault,
        OptionsConfig options,
        List<CatalogedConfig> catalogs,
        List<RouteConfig> routes,
        TelemetryRefConfig telemetryRef);
}
