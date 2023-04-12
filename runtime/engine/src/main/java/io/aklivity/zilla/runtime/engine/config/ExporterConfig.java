package io.aklivity.zilla.runtime.engine.config;

import static java.util.Objects.requireNonNull;

public class ExporterConfig
{
    public final String name;
    public final String type;
    public final OptionsConfig options;

    public transient long id;

    public ExporterConfig(
        String name,
        String type,
        OptionsConfig options)
    {
        this.name = requireNonNull(name);
        this.type = requireNonNull(type);
        this.options = options;
    }
}
