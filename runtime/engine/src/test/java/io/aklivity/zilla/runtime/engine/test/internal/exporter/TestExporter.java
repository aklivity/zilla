package io.aklivity.zilla.runtime.engine.test.internal.exporter;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;

public class TestExporter implements Exporter
{
    public TestExporter(
        Configuration config)
    {
    }

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public URL type()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public ExporterContext supply(
        EngineContext context)
    {
        return new TestExporterContext();
    }
}
