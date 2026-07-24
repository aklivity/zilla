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
package io.aklivity.zilla.config.exporter.prometheus;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PrometheusOptionsConfigBuilder<T> extends ConfigBuilder<T, PrometheusOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private PrometheusEndpointConfig[] endpoints;

    PrometheusOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<PrometheusOptionsConfigBuilder<T>> thisType()
    {
        return (Class<PrometheusOptionsConfigBuilder<T>>) getClass();
    }

    public PrometheusOptionsConfigBuilder<T> endpoints(
        PrometheusEndpointConfig[] endpoints)
    {
        this.endpoints = endpoints;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new PrometheusOptionsConfig(endpoints));
    }
}
