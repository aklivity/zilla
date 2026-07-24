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

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.List;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public class RouteConfig
{
    public transient long id;
    public transient LongObjectPredicate<UnaryOperator<String>> authorized;
    public transient ToLongFunction<String> resolveId;

    public final int order;
    public final String exit;
    public final List<ConditionConfig> when;
    public final WithConfig with;
    public final List<GuardedConfig> guarded;

    public static GenericRouteConfigBuilder<RouteConfig> builder()
    {
        return new GenericRouteConfigBuilder<>(identity());
    }

    RouteConfig(
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
