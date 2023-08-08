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
package io.aklivity.zilla.runtime.binding.ws.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;

import io.aklivity.zilla.runtime.binding.ws.config.WsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class WsBindingConfig
{
    private static final WsOptionsConfig DEFAULT_OPTIONS = new WsOptionsConfig(null, null, null, null);

    public final long id;
    public final String name;
    public final WsOptionsConfig options;
    public final KindConfig kind;
    public final List<WsRouteConfig> routes;

    public WsBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = binding.options instanceof WsOptionsConfig ? (WsOptionsConfig) binding.options : DEFAULT_OPTIONS;
        this.routes = binding.routes.stream().map(WsRouteConfig::new).collect(toList());
    }

    public WsRouteConfig resolve(
        long authorization,
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(protocol, scheme, authority, path))
            .findFirst()
            .orElse(null);
    }
}
