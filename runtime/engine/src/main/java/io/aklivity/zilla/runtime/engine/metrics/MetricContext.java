package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public interface MetricContext
{
    String group();

    Metric.Kind kind();

    MetricHandler supply(
        LongConsumer recorder);
}
