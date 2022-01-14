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
package io.aklivity.zilla.runtime.binding.proxy.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.proxy.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.RoleConfig;

public final class ProxyBindingConfig
{
    public final long routeId;
    public final String entry;
    public final RoleConfig kind;
    public final ProxyOptionsConfig options;
    public final List<ProxyRouteConfig> routes;
    public final ProxyRouteConfig exit;

    public ProxyBindingConfig(
        BindingConfig binding)
    {
        this.routeId = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = ProxyOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(ProxyRouteConfig::new).collect(toList());
        this.exit = binding.exit != null ? new ProxyRouteConfig(binding.exit) : null;
    }

    public ProxyRouteConfig resolve(
        long authorization,
        ProxyBeginExFW beginEx)
    {
        return routes.stream()
                .filter(r -> r.when.isEmpty() || r.when.stream().anyMatch(c -> c.matches(beginEx)))
                .findFirst()
                .orElse(exit);
    }
}
