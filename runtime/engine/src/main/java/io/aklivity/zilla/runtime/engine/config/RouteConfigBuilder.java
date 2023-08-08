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

public final class RouteConfigBuilder<T> implements ConfigBuilder<T>
{
    public static final List<ConditionConfig> WHEN_DEFAULT = emptyList();
    public static final List<GuardedConfig> GUARDED_DEFAULT = emptyList();

    private final Function<RouteConfig, T> mapper;

    private int order;
    private String exit;
    private List<ConditionConfig> when;
    private WithConfig with;
    private List<GuardedConfig> guarded;

    RouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public RouteConfigBuilder<T> order(
        int order)
    {
        this.order = order;
        return this;
    }

    public RouteConfigBuilder<T> exit(
        String exit)
    {
        this.exit = exit;
        return this;
    }

    public <B extends ConfigBuilder<RouteConfigBuilder<T>>> B when(
        Function<RouteConfigBuilder<T>, B> when)
    {
        return when.apply(this);
    }

    public RouteConfigBuilder<T> when(
        ConditionConfig condition)
    {
        if (when == null)
        {
            when = new LinkedList<>();
        }
        when.add(condition);
        return this;
    }

    public <B extends ConfigBuilder<RouteConfigBuilder<T>>> B with(
        Function<Function<WithConfig, RouteConfigBuilder<T>>, B> with)
    {
        return with.apply(this::with);
    }

    public RouteConfigBuilder<T> with(
        WithConfig with)
    {
        this.with = with;
        return this;
    }

    public GuardedConfigBuilder<RouteConfigBuilder<T>> guarded()
    {
        return new GuardedConfigBuilder<>(this::guarded);
    }

    public RouteConfigBuilder<T> guarded(
        GuardedConfig guarded)
    {
        if (this.guarded == null)
        {
            this.guarded = new LinkedList<>();
        }
        this.guarded.add(guarded);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new RouteConfig(
            order,
            exit,
            Optional.ofNullable(when).orElse(WHEN_DEFAULT),
            with,
            Optional.ofNullable(guarded).orElse(GUARDED_DEFAULT)));
    }
}
