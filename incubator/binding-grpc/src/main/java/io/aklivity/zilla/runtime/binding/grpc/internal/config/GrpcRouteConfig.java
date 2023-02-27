/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class GrpcRouteConfig extends OptionsConfig
{
    public final long id;

    private final List<GrpcConditionMatcher> when;
    private final LongPredicate authorized;

    public GrpcRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(GrpcConditionConfig.class::cast)
            .map(GrpcConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        CharSequence path)
    {
        return when.isEmpty() || path != null && when.stream().anyMatch(m -> m.matches(path));
    }
}
