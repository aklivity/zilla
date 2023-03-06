package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

public class AttributeConfig
{
    public transient long id;

    public final String name;
    public final String value;

    public AttributeConfig(
        String name,
        String value)
    {
        this.name = requireNonNull(name);
        this.value = requireNonNull(value);
    }
}
