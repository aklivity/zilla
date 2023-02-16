package io.aklivity.zilla.runtime.engine.metrics;

public interface MetricsContext
{
    Metric resolve(
            String name);
}
