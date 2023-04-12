package io.aklivity.zilla.runtime.engine.test.internal.exporter;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterFactorySpi;

public final class TestExporterFactorySpi implements ExporterFactorySpi
{
    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public Exporter create(
        Configuration config)
    {
        return new TestExporter(config);
    }
}
