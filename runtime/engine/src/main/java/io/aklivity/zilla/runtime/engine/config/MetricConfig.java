package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

public class MetricConfig
{
    public transient long id;

    public final String name;

    public MetricConfig(
        String name)
    {
        this.name = requireNonNull(name);
    }
}
