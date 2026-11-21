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
package io.aklivity.zilla.config.binding.a2a.openapi;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.RouteConfigBuilder;

public final class A2aOpenapiRouteConfigBuilder<T> extends RouteConfigBuilder<T, A2aOpenapiRouteConfigBuilder<T>>
{
    A2aOpenapiRouteConfigBuilder(
        Function<RouteConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aOpenapiRouteConfigBuilder<T>> thisType()
    {
        return (Class<A2aOpenapiRouteConfigBuilder<T>>) getClass();
    }

    public A2aOpenapiConditionConfigBuilder<A2aOpenapiRouteConfigBuilder<T>> when()
    {
        return new A2aOpenapiConditionConfigBuilder<>(this::when);
    }

    public A2aOpenapiWithConfigBuilder<A2aOpenapiRouteConfigBuilder<T>> with()
    {
        return new A2aOpenapiWithConfigBuilder<>(this::with);
    }
}
