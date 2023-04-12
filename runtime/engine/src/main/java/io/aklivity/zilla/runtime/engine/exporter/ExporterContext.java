package io.aklivity.zilla.runtime.engine.exporter;

import io.aklivity.zilla.runtime.engine.config.ExporterConfig;

public interface ExporterContext
{
    ExporterHandler attach(
        ExporterConfig config);

    void detach(
        long exporterId);
}
