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
package io.aklivity.zilla.runtime.cog.ws.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

public final class WsBinding
{
    private static final WsOptions DEFAULT_OPTIONS = new WsOptions(null, null, null, null);

    public final long id;
    public final String entry;
    public final WsOptions options;
    public final Role kind;
    public final List<WsRoute> routes;
    public final WsRoute exit;

    public WsBinding(
        Binding binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = binding.options instanceof WsOptions ? (WsOptions) binding.options : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(WsRoute::new).collect(toList());
        this.exit = binding.exit != null ? new WsRoute(binding.exit) : null;
    }

    public WsRoute resolve(
        long authorization,
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        return routes.stream()
            .filter(r -> r.when.isEmpty() || r.when.stream().anyMatch(m -> m.matches(protocol, scheme, authority, path)))
            .findFirst()
            .orElse(exit);
    }
}
