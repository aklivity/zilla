package io.aklivity.zilla.runtime.engine.metrics;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TestHistogramMetric implements Metric
{
    private static final String GROUP = "test";
    private static final String NAME = GROUP + ".histogram";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return HISTOGRAM;
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
                return HISTOGRAM;
            }

            @Override
            public MessageConsumer supply(
                LongConsumer recorder)
            {
                return MessageConsumer.NOOP;
            }
        };
    }
}
