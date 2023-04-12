package io.aklivity.zilla.runtime.engine.test.internal.exporter;

import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
public class TestExporterContext implements ExporterContext
{
    @Override
    public ExporterHandler attach(
        ExporterConfig config)
    {
        return new TestExporterHandler();
    }

    @Override
    public void detach(
        long exporterId)
    {
    }
}
