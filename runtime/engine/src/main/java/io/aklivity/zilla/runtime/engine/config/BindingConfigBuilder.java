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

public final class BindingConfigBuilder<T> implements ConfigBuilder<T>
{
    public static final List<RouteConfig> ROUTES_DEFAULT = emptyList();

    private final Function<BindingConfig, T> mapper;

    private String vault;
    private String name;
    private String type;
    private KindConfig kind;
    private String entry;
    private OptionsConfig options;
    private List<RouteConfig> routes;
    private TelemetryRefConfig telemetry;

    BindingConfigBuilder(
        Function<BindingConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public BindingConfigBuilder<T> vault(
        String vault)
    {
        this.vault = vault;
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

    public <B extends ConfigBuilder<BindingConfigBuilder<T>>> B options(
        Function<BindingConfigBuilder<T>, B> options)
    {
        return options.apply(this);
    }

    public BindingConfigBuilder<T> options(
        OptionsConfig options)
    {
        this.options = options;
        return this;
    }

    public RouteConfigBuilder<BindingConfigBuilder<T>> route()
    {
        return new RouteConfigBuilder<>(this::route);
    }

    public BindingConfigBuilder<T> route(
        RouteConfig route)
    {
        if (routes == null)
        {
            routes = new LinkedList<>();
        }
        routes.add(route);
        return this;
    }

    public TelemetryRefConfigBuilder<BindingConfigBuilder<T>> telemetry()
    {
        return new TelemetryRefConfigBuilder<>(this::telemetry);
    }

    public BindingConfigBuilder<T> telemetry(
        TelemetryRefConfig telemetry)
    {
        this.telemetry = telemetry;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new BindingConfig(
            vault,
            name,
            type,
            kind,
            entry,
            options,
            Optional.ofNullable(routes).orElse(ROUTES_DEFAULT),
            telemetry));
    }
}
