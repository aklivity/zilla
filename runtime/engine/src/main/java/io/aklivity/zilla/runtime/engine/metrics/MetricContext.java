package io.aklivity.zilla.runtime.engine.metrics;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;

import java.util.function.LongConsumer;

@FunctionalInterface
public interface MetricContext
{
    default Metric.Kind kind()
    {
        return COUNTER;
    }

    MetricHandler supply(
        LongConsumer recorder);
}
