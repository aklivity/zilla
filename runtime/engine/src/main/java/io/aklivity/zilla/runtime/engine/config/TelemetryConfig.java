package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public class TelemetryConfig
{
    public static final TelemetryConfig EMPTY = new TelemetryConfig(List.of(), List.of(), List.of());

    public final List<AttributeConfig> attributes;
    public final List<MetricConfig> metrics;
    public final List<ExporterConfig> exporters;

    public TelemetryConfig(
        List<AttributeConfig> attributes,
        List<MetricConfig> metrics,
        List<ExporterConfig> exporters)
    {
        this.attributes = attributes;
        this.metrics = metrics;
        this.exporters = exporters;
    }
}
