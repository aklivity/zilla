package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public class TelemetryConfig
{
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
