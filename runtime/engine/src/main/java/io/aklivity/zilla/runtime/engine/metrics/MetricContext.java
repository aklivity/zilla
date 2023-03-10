package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public interface MetricContext
{
    MetricHandler supply(
        LongConsumer recorder);
}
