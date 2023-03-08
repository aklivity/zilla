package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.engine.metrics.MetricsContext;

public class MetricRegistry
{
    private final MetricConfig config;
    private final MetricsContext context;

    private MetricHandler attached;

    MetricRegistry(
        MetricConfig vault,
        MetricsContext context)
    {
        this.config = requireNonNull(vault);
        this.context = requireNonNull(context);
    }

    /*public void attach()
    {
        attached = context.attach(config);
    }

    public void detach()
    {
        context.detach(config);
        attached = null;
    }*/

    public MetricHandler handler()
    {
        return attached;
    }
}
