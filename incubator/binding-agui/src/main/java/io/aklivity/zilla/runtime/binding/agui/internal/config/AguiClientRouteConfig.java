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
package io.aklivity.zilla.runtime.binding.agui.internal.config;

import static java.util.function.UnaryOperator.identity;

import java.net.URI;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.aklivity.zilla.config.binding.agui.AguiConditionConfig;
import io.aklivity.zilla.config.binding.agui.AguiWithConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class AguiClientRouteConfig
{
    public final long id;
    public final String path;

    private final List<String> agents;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public AguiClientRouteConfig(
        RouteConfig route,
        URI server,
        String serverPath)
    {
        final AguiWithConfig with = (AguiWithConfig) route.with;

        this.id = route.id;
        this.agents = route.when.stream()
            .map(AguiConditionConfig.class::cast)
            .flatMap(c -> Stream.ofNullable(c.agent))
            .toList();
        this.path = with != null && with.path != null ? server.resolve(with.path).getRawPath() : serverPath;
        this.authorized = route.authorized;
    }

    public boolean matches(
        String agent,
        long authorization)
    {
        return (agents.isEmpty() || agents.contains(agent)) && authorized.test(authorization, identity());
    }
}
