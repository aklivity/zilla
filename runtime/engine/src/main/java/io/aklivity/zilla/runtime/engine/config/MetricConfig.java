package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

public class MetricConfig
{
    public final String group;
    public final String name;

    public transient long id;

    public MetricConfig(
        String group,
        String name)
    {
        this.group = requireNonNull(group);
        this.name = requireNonNull(name);
    }
}
