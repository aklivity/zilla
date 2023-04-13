package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public interface MetricContext
{
    String group();

    Metric.Kind kind();

    MessageConsumer supply(
        LongConsumer recorder);
}
