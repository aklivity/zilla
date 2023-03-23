package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public interface MetricContext
{
    default Metric metric()
    {
        return new TestCounterMetric();
    }

    MetricHandler supply(
        LongConsumer recorder);
}
