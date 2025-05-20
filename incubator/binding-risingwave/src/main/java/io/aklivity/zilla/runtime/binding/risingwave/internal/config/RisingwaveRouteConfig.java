/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.risingwave.config.RisingwaveConditionConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class RisingwaveRouteConfig
{
    public final long id;

    private final List<RisingwaveConditionMatcher> when;
    private final LongObjectPredicate authorized;

    public RisingwaveRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.when = route.when.stream()
            .map(RisingwaveConditionConfig.class::cast)
            .map(RisingwaveConditionMatcher::new)
            .collect(toList());
        this.authorized = route.authorized;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        DirectBuffer statement,
        int offset,
        int length)
    {
        return when.isEmpty() ||
            statement != null && when.stream().anyMatch(m -> m.matches(statement, offset, length));
    }
}
