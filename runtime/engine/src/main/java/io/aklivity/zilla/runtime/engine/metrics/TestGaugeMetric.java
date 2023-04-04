package io.aklivity.zilla.runtime.engine.metrics;

import io.aklivity.zilla.runtime.engine.EngineContext;

public class TestGaugeMetric implements Metric
{
    private static final String NAME = "test.gauge";

    @Override
    public String name()
    {
        return NAME;
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
    public MetricContext supply(
        EngineContext context)
    {
        return recorder -> MetricHandler.NO_OP;
    }
}
