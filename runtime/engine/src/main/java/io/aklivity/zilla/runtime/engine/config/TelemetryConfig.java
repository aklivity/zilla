package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public class TelemetryConfig
{
    public static final TelemetryConfig EMPTY = new TelemetryConfig(List.of(), List.of());

    public final List<AttributeConfig> attributes;
    public final List<MetricConfig> metrics;

    public TelemetryConfig(
        List<AttributeConfig> attributes,
        List<MetricConfig> metrics)
    {
        this.attributes = attributes;
        this.metrics = metrics;
    }
}
