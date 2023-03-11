package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

public class MetricRegistry
{
    private final MetricContext context;

    private MetricHandler attached;

    MetricRegistry(
        MetricContext context)
    {
        this.context = requireNonNull(context);
    }

    public void attach(LongConsumer metricRecorder)
    {
        attached = context.supply(metricRecorder);
    }

    public void detach()
    {
        attached = null;
    }

    public MetricHandler handler()
    {
        return attached;
    }
}
