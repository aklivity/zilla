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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.LongPredicate;

import io.aklivity.zilla.runtime.engine.config.RouteConfig;

public final class OpenapiRouteConfig
{
    public final long id;
    public final List<OpenapiConditionConfig> when;
    public final OpenapiWithConfig with;

    private final LongPredicate authorized;

    public OpenapiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.authorized = route.authorized;
        this.when = route.when.stream()
            .map(OpenapiConditionConfig.class::cast)
            .collect(toList());
        this.with = (OpenapiWithConfig) route.with;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization);
    }

    boolean matches(
        String apiId,
        String operationId)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(apiId, operationId));
    }
}
