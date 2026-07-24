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

public abstract class RouteConfigBuilder<T, R extends RouteConfigBuilder<T, R>> extends ConfigBuilder<T, R>
{
    public static final List<ConditionConfig> WHEN_DEFAULT = emptyList();
    public static final List<GuardedConfig> GUARDED_DEFAULT = emptyList();

    private final Function<RouteConfig, T> mapper;

    private int order;
    private String exit;
    private List<ConditionConfig> when;
    private WithConfig with;
    private List<GuardedConfig> guarded;

    protected RouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public R order(
        int order)
    {
        this.order = order;
        return thisType().cast(this);
    }

    public R exit(
        String exit)
    {
        this.exit = exit;
        return thisType().cast(this);
    }

    public <C extends ConfigBuilder<R, C>> C when(
        Function<Function<ConditionConfig, R>, C> condition)
    {
        return condition.apply(this::when);
    }

    public R when(
        ConditionConfig condition)
    {
        if (when == null)
        {
            when = new LinkedList<>();
        }
        when.add(condition);
        return thisType().cast(this);
    }

    public <B extends ConfigBuilder<R, B>> B with(
        Function<Function<WithConfig, R>, B> with)
    {
        return with.apply(this::with);
    }

    public R with(
        WithConfig with)
    {
        this.with = with;
        return thisType().cast(this);
    }

    public GuardedConfigBuilder<R> guarded()
    {
        return new GuardedConfigBuilder<>(this::guarded);
    }

    public R guarded(
        GuardedConfig guarded)
    {
        if (this.guarded == null)
        {
            this.guarded = new LinkedList<>();
        }
        this.guarded.add(guarded);
        return thisType().cast(this);
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
