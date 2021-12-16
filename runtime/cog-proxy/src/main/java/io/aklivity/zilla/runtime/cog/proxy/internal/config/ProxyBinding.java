/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.proxy.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

public final class ProxyBinding
{
    public final long routeId;
    public final String entry;
    public final Role kind;
    public final ProxyOptions options;
    public final List<ProxyRoute> routes;
    public final ProxyRoute exit;

    public ProxyBinding(
        Binding binding)
    {
        this.routeId = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = ProxyOptions.class.cast(binding.options);
        this.routes = binding.routes.stream().map(ProxyRoute::new).collect(toList());
        this.exit = binding.exit != null ? new ProxyRoute(binding.exit) : null;
    }

    public ProxyRoute resolve(
        long authorization,
        ProxyBeginExFW beginEx)
    {
        return routes.stream()
                .filter(r -> r.when.stream().allMatch(c -> c.matches(beginEx)))
                .findFirst()
                .orElse(exit);
    }
}
