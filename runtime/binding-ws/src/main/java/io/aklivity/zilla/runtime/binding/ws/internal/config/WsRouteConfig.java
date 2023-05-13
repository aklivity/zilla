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
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class WsRouteConfig extends OptionsConfig
{
    public final long id;
    public final int order;

    private final List<WsConditionMatcher> when;
    private final LongPredicate authorized;

    public WsRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.order = route.order;
        this.when = route.when.stream()
            .map(WsConditionConfig.class::cast)
            .map(WsConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(protocol, scheme, authority, path));
    }
}
