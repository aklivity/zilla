package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public class TelemetryRefConfig
{
    public final List<MetricRefConfig> metricRefs;

    public TelemetryRefConfig(
        List<MetricRefConfig> metricRefs)
    {
        this.metricRefs = metricRefs;
    }
}
