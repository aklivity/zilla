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

public class RouteConfig
{
    private static final List<ConditionConfig> UNCONDITIONAL = emptyList();

    public transient long id;

    public final int order;
    public final String exit;
    public final List<ConditionConfig> when;
    public final WithConfig with;

    public RouteConfig(
        String exit)
    {
        this(0, exit);
    }

    public RouteConfig(
        String exit,
        List<ConditionConfig> when)
    {
        this(0, exit, when);
    }

    public RouteConfig(
        int order,
        String exit)
    {
        this(order, exit, UNCONDITIONAL);
    }

    public RouteConfig(
        int order,
        String exit,
        List<ConditionConfig> when)
    {
        this(order, exit, when, null);
    }

    public RouteConfig(
        int order,
        String exit,
        List<ConditionConfig> when,
        WithConfig with)
    {
        this.order = order;
        this.exit = requireNonNull(exit);
        this.when = requireNonNull(when);
        this.with = with;
    }
}
