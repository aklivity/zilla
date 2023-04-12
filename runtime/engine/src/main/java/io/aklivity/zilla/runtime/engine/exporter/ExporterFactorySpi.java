package io.aklivity.zilla.runtime.engine.exporter;

import io.aklivity.zilla.runtime.engine.Configuration;

public interface ExporterFactorySpi
{
    String type();

    Exporter create(
        Configuration config);
}
