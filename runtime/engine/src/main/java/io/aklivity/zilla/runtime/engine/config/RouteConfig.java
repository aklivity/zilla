/*
 * Copyright 2021-2022 Aklivity Inc.
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
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.LongPredicate;

public class RouteConfig
{
    public static final List<ConditionConfig> WHEN_DEFAULT = emptyList();
    public static final List<GuardedConfig> GUARDED_DEFAULT = emptyList();

    public transient long id;
    public transient LongPredicate authorized;

    public final int order;
    public final String exit;
    public final List<ConditionConfig> when;
    public final WithConfig with;
    public final List<GuardedConfig> guarded;

    public RouteConfig(
        String exit)
    {
        this(0, exit);
    }

    public RouteConfig(
        String exit,
        List<GuardedConfig> guarded)
    {
        this(exit, WHEN_DEFAULT, guarded);
    }

    public RouteConfig(
        String exit,
        List<ConditionConfig> when,
        List<GuardedConfig> guarded)
    {
        this(0, exit, when, null, guarded);
    }

    public RouteConfig(
        int order,
        String exit)
    {
        this(order, exit, WHEN_DEFAULT, null, GUARDED_DEFAULT);
    }

    public RouteConfig(
        int order,
        String exit,
        List<ConditionConfig> when,
        WithConfig with,
        List<GuardedConfig> guarded)
    {
        this.order = order;
        this.exit = exit;
        this.when = requireNonNull(when);
        this.with = with;
        this.guarded = requireNonNull(guarded);
    }
}
