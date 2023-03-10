package io.aklivity.zilla.runtime.engine.metrics;

import io.aklivity.zilla.runtime.engine.EngineContext;

public class TestHistogramMetric implements Metric
{
    private static final String NAME = "test.histogram";
    @Override
    public String name()
    {
        return NAME;
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
    public MetricContext supply(
        EngineContext context)
    {
        // TODO: Ati
        return null;
    }
}
