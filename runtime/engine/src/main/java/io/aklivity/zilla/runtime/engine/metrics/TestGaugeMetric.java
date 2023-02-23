package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public class TestGaugeMetric implements Metric
{
    @Override
    public String name()
    {
        return "test.gauge";
    }

    @Override
    public Kind kind()
    {
        return Kind.GAUGE;
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
