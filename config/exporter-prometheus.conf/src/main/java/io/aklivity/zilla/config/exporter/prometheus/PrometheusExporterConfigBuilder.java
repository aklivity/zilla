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

import io.aklivity.zilla.config.engine.ExporterConfig;
import io.aklivity.zilla.config.engine.ExporterConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.exporter.prometheus.internal.PrometheusExporterInfo;

public final class PrometheusExporterConfigBuilder<T> extends ExporterConfigBuilder<T, PrometheusExporterConfigBuilder<T>>
{
    PrometheusExporterConfigBuilder(
        Function<ExporterConfig, T> mapper)
    {
        super(mapper);
        type(PrometheusExporterInfo.TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<PrometheusExporterConfigBuilder<T>> thisType()
    {
        return (Class<PrometheusExporterConfigBuilder<T>>) getClass();
    }

    public PrometheusOptionsConfigBuilder<PrometheusExporterConfigBuilder<T>> options()
    {
        return new PrometheusOptionsConfigBuilder<>(this::options);
    }

    @Override
    protected ExporterConfig newExporter(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        return new PrometheusExporterConfig(namespace, name, type, vault, options);
    }
}
