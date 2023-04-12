package io.aklivity.zilla.runtime.engine.exporter;

public interface ExporterHandler
{
    void start();

    int export();

    void stop();
}
