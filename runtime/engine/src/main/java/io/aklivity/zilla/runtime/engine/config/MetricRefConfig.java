package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

public class MetricRefConfig
{
    public final String name;

    public MetricRefConfig(
        String name)
    {
        this.name = requireNonNull(name);
    }
}
