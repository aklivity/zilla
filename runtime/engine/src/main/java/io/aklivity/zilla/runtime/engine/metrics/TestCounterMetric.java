package io.aklivity.zilla.runtime.engine.metrics;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.EngineContext;

public class TestCounterMetric implements Metric
{
    private static final String GROUP = "test";
    private static final String NAME = GROUP + ".counter";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return COUNTER;
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
        return new MetricContext()
        {
            @Override
            public String group()
            {
                return GROUP;
            }

            @Override
            public Kind kind()
            {
                return COUNTER;
            }

            @Override
            public MetricHandler supply(
                LongConsumer recorder)
            {
                return MetricHandler.NO_OP;
            }
        };
    }
}
