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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.TelemetryRefConfig;

public final class OpenapiAsyncapiBindingConfigBuilder<T> extends BindingConfigBuilder<T, OpenapiAsyncapiBindingConfigBuilder<T>>
{
    OpenapiAsyncapiBindingConfigBuilder(
        Function<BindingConfig, T> mapper)
    {
        super(mapper);
        type(OpenapiAsyncapiBindingInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiAsyncapiBindingConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiAsyncapiBindingConfigBuilder<T>>) getClass();
    }

    public OpenapiAsyncapiOptionsConfigBuilder<OpenapiAsyncapiBindingConfigBuilder<T>> options()
    {
        return new OpenapiAsyncapiOptionsConfigBuilder<>(this::options);
    }

    @Override
    public OpenapiAsyncapiRouteConfigBuilder<OpenapiAsyncapiBindingConfigBuilder<T>> route()
    {
        return new OpenapiAsyncapiRouteConfigBuilder<>(this::route)
            .order(nextRouteOrder());
    }

    @Override
    protected BindingConfig newBinding(
        String namespace,
        String name,
        String type,
        KindConfig kind,
        String entry,
        String vault,
        OptionsConfig options,
        List<CatalogedConfig> catalogs,
        List<RouteConfig> routes,
        TelemetryRefConfig telemetryRef)
    {
        return new OpenapiAsyncapiBindingConfig(
            namespace, name, type, kind, entry, vault, options, catalogs, routes, telemetryRef);
    }
}
