package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

public class MetricRegistry
{
    private final MetricConfig config;

    private MetricHandler attached;

    MetricRegistry(
            MetricConfig config)
    {
        this.config = requireNonNull(config);
    }

    // TODO: Ati
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
