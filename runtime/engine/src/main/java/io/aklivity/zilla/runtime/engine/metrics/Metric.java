package io.aklivity.zilla.runtime.engine.metrics;

import io.aklivity.zilla.runtime.engine.EngineContext;

public interface Metric
{
    enum Kind
    {
        COUNTER,
        GAUGE,
        HISTOGRAM
    }

    enum Unit
    {
        BYTES,
        COUNT
    }

    String name();

    Kind kind();

    Unit unit();

    MetricContext supply(
        EngineContext context);
}
