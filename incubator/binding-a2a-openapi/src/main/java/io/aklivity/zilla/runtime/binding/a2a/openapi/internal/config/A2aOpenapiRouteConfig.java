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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiConditionConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiWithConfig;
import io.aklivity.zilla.config.engine.GuardedConfig;
import io.aklivity.zilla.config.engine.RouteConfig;

public final class A2aOpenapiRouteConfig
{
    public final long id;
    public final List<A2aOpenapiConditionConfig> when;
    public final A2aOpenapiWithConfig with;
    public final List<GuardedConfig> guarded;
    public final String exit;

    public A2aOpenapiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(A2aOpenapiConditionConfig.class::cast)
            .collect(toList());
        this.with = (A2aOpenapiWithConfig) route.with;
        this.guarded = route.guarded;
        this.exit = route.exit != null ? route.qname() : null;
    }

    public boolean isBulk()
    {
        return with.tag != null ||
            with.operation == null ||
            with.operation.indexOf('*') != -1;
    }
}
