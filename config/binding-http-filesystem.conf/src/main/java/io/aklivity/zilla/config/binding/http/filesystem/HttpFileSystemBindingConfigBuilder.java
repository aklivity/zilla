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
package io.aklivity.zilla.config.binding.http.filesystem;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.binding.http.filesystem.internal.HttpFileSystemBindingInfo;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingConfigBuilder;
import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.config.engine.TelemetryRefConfig;

public final class HttpFileSystemBindingConfigBuilder<T> extends BindingConfigBuilder<T, HttpFileSystemBindingConfigBuilder<T>>
{
    HttpFileSystemBindingConfigBuilder(
        Function<BindingConfig, T> mapper)
    {
        super(mapper);
        type(HttpFileSystemBindingInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpFileSystemBindingConfigBuilder<T>> thisType()
    {
        return (Class<HttpFileSystemBindingConfigBuilder<T>>) getClass();
    }

    @Override
    public HttpFileSystemRouteConfigBuilder<HttpFileSystemBindingConfigBuilder<T>> route()
    {
        return new HttpFileSystemRouteConfigBuilder<>(this::route)
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
        return new HttpFileSystemBindingConfig(
            namespace, name, type, kind, entry, vault, options, catalogs, routes, telemetryRef);
    }
}
