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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.config.engine.BindingConfig;

public final class AguiA2aBindingConfig
{
    public final long id;
    public final String name;
    public final List<AguiA2aRouteConfig> routes;

    public AguiA2aBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.routes = binding.routes.stream().map(AguiA2aRouteConfig::new).collect(toList());
    }

    public AguiA2aRouteConfig resolveRoute(
        long authorization,
        String agent)
    {
        return routes.stream()
            .filter(r -> r.matchesAgent(agent) && r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }
}
