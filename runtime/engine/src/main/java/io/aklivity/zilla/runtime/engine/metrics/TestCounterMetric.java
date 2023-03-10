package io.aklivity.zilla.runtime.engine.metrics;

import io.aklivity.zilla.runtime.engine.EngineContext;

public class TestCounterMetric implements Metric
{
    private static final String NAME = "test.counter";

    @Override
    public String name()
    {
        return NAME;
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
    public MetricContext supply(
        EngineContext context)
    {
        // TODO: Ati
        return null;
    }
}
