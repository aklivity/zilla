package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

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

    MetricHandler supply(LongConsumer recorder);
}
