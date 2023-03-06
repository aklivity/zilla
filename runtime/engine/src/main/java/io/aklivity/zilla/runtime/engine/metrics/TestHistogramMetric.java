package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public class TestHistogramMetric implements Metric
{
    @Override
    public String name()
    {
        return "test.histogram";
    }

    @Override
    public Kind kind()
    {
        return Kind.HISTOGRAM;
    }

    @Override
    public Unit unit()
    {
        return Unit.COUNT;
    }

    @Override
    public MetricHandler supply(
        LongConsumer recorder)
    {
        return null;
    }
}
