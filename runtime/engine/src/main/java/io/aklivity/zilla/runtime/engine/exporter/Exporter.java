package io.aklivity.zilla.runtime.engine.exporter;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;

public interface Exporter
{
    String name();

    URL type();

    ExporterContext supply(
        EngineContext context);

    default int maxWorkers()
    {
        return 1;
    }
}
