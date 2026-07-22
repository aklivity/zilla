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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.config.binding.asyncapi.AsyncapiConditionConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.common.lang.util.function.LongObjectPredicate;

public final class AsyncapiRouteConfig
{
    public final long id;
    public final List<AsyncapiConditionConfig> when;
    public final AsyncapiWithConfig with;

    private final LongObjectPredicate<UnaryOperator<String>> authorized;

    public AsyncapiRouteConfig(
        RouteConfig route)
    {
        this.id = route.id;
        this.authorized = route.authorized;
        this.when = route.when.stream()
            .map(AsyncapiConditionConfig.class::cast)
            .collect(toList());
        this.with = (AsyncapiWithConfig) route.with;
    }

    boolean authorized(
        long authorization)
    {
        return authorized.test(authorization, identity());
    }

    boolean matches(
        String spec,
        String operation,
        List<String> tags,
        List<AsyncapiServerView> operationServers)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(spec, operation, tags, operationServers));
    }
}
