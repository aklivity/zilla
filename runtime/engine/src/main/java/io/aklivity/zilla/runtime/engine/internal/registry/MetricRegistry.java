package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

public class MetricRegistry
{
    private final MetricContext context;

    MetricRegistry(
        MetricContext context)
    {
        this.context = requireNonNull(context);
    }

    public MetricHandler supplyHandler(
        LongConsumer recorder)
    {
        return context.supply(recorder);
    }

    public String group()
    {
        return context.group();
    }

    public Metric.Kind kind()
    {
        return context.kind();
    }
}
