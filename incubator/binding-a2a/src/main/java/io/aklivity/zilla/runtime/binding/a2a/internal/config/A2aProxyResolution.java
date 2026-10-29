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
package io.aklivity.zilla.runtime.binding.a2a.internal.config;

import java.util.List;

public final class A2aProxyResolution
{
    public enum Outcome
    {
        ROUTED,
        AGENT_REQUIRED,
        AGENT_NOT_FOUND,
        FORBIDDEN
    }

    private static final A2aProxyResolution AGENT_REQUIRED = new A2aProxyResolution(Outcome.AGENT_REQUIRED, null, List.of());
    private static final A2aProxyResolution AGENT_NOT_FOUND = new A2aProxyResolution(Outcome.AGENT_NOT_FOUND, null, List.of());

    public final Outcome outcome;
    public final A2aProxyRouteConfig route;
    public final List<String> roles;

    public static A2aProxyResolution routed(
        A2aProxyRouteConfig route)
    {
        return new A2aProxyResolution(Outcome.ROUTED, route, List.of());
    }

    public static A2aProxyResolution agentRequired()
    {
        return AGENT_REQUIRED;
    }

    public static A2aProxyResolution agentNotFound()
    {
        return AGENT_NOT_FOUND;
    }

    public static A2aProxyResolution forbidden(
        List<String> roles)
    {
        return new A2aProxyResolution(Outcome.FORBIDDEN, null, roles);
    }

    private A2aProxyResolution(
        Outcome outcome,
        A2aProxyRouteConfig route,
        List<String> roles)
    {
        this.outcome = outcome;
        this.route = route;
        this.roles = roles;
    }
}
