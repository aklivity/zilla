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
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class PrometheusExporterConfig extends ExporterConfig
{
    public static PrometheusExporterConfigBuilder<PrometheusExporterConfig> builder()
    {
        return new PrometheusExporterConfigBuilder<>(PrometheusExporterConfig.class::cast);
    }

    public static <T> PrometheusExporterConfigBuilder<T> builder(
        Function<ExporterConfig, T> mapper)
    {
        return new PrometheusExporterConfigBuilder<>(mapper);
    }

    PrometheusExporterConfig(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        super(namespace, name, type, vault, options);
    }
}
