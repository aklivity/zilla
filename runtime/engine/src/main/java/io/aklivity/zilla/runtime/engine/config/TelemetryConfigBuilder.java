/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class TelemetryConfigBuilder<T> implements ConfigBuilder<T>
{
    public static final List<AttributeConfig> ATTRIBUTES_DEFAULT = List.of();
    public static final List<MetricConfig> METRICS_DEFAULT = List.of();
    public static final List<ExporterConfig> EXPORTERS_DEFAULT = List.of();

    private final Function<TelemetryConfig, T> mapper;

    private List<AttributeConfig> attributes;
    private List<MetricConfig> metrics;
    private List<ExporterConfig> exporters;

    TelemetryConfigBuilder(
        Function<TelemetryConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    public AttributeConfigBuilder<TelemetryConfigBuilder<T>> attribute()
    {
        return new AttributeConfigBuilder<>(this::attribute);
    }

    public TelemetryConfigBuilder<T> attribute(
        AttributeConfig attribute)
    {
        if (attributes == null)
        {
            attributes = new LinkedList<>();
        }
        attributes.add(attribute);
        return this;
    }

    public MetricConfigBuilder<TelemetryConfigBuilder<T>> metric()
    {
        return new MetricConfigBuilder<>(this::metric);
    }

    public TelemetryConfigBuilder<T> metric(
        MetricConfig metric)
    {
        if (metrics == null)
        {
            metrics = new LinkedList<>();
        }
        metrics.add(metric);
        return this;
    }

    public ExporterConfigBuilder<TelemetryConfigBuilder<T>> exporter()
    {
        return new ExporterConfigBuilder<>(this::exporter);
    }

    public TelemetryConfigBuilder<T> exporter(
        ExporterConfig exporter)
    {
        if (exporters == null)
        {
            exporters = new LinkedList<>();
        }
        exporters.add(exporter);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new TelemetryConfig(
            Optional.ofNullable(attributes).orElse(ATTRIBUTES_DEFAULT),
            Optional.ofNullable(metrics).orElse(METRICS_DEFAULT),
            Optional.ofNullable(exporters).orElse(EXPORTERS_DEFAULT)));
    }
}
