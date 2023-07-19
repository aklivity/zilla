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
package io.aklivity.zilla.runtime.binding.tcp.internal.config;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.net.InetSocketAddress;
import java.util.List;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public final class TcpBindingConfig
{
    private static final List<TcpRouteConfig> DEFAULT_CLIENT_ROUTES = initDefaultClientRoutes();

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final TcpOptionsConfig options;
    public final List<TcpRouteConfig> routes;

    private PollerKey[] attached;

    public TcpBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = TcpOptionsConfig.class.cast(binding.options);
        this.routes = binding.kind == KindConfig.CLIENT && binding.routes.isEmpty()
                ? DEFAULT_CLIENT_ROUTES
                : binding.routes.stream().map(TcpRouteConfig::new).collect(toList());
    }

    public PollerKey[] attach(
        PollerKey[] attachment)
    {
        PollerKey[] detached = attached;
        attached = attachment;
        return detached;
    }

    public TcpRouteConfig resolve(
        InetSocketAddress local,
        InetSocketAddress remote)
    {
        return routes.stream()
            .filter(r -> r.matches(local))
            .findFirst()
            .orElse(null);
    }

    private static List<TcpRouteConfig> initDefaultClientRoutes()
    {
        final RouteConfig route = new RouteConfig(null);
        route.authorized = id -> true;

        return singletonList(new TcpRouteConfig(route));
    }
}
