package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

public class TestCounterMetric implements Metric
{
    @Override
    public String name()
    {
        return "test.counter";
    }

    @Override
    public Kind kind()
    {
        return Kind.COUNTER;
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
