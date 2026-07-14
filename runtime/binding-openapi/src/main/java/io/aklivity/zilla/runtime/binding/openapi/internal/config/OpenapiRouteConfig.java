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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class OpenapiRouteConfig
{
    public final long id;
    public final List<OpenapiConditionConfig> when;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public OpenapiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.authorized = route.authorized;
        this.when = route.when.stream()
            .map(OpenapiConditionConfig.class::cast)
            .collect(toList());
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String spec,
        String operation,
        List<String> tags)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(spec, operation, tags));
    }

    boolean includes(
        OpenapiOperationView operation)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matchesServers(operation.servers));
    }
}
