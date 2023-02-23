package io.aklivity.zilla.runtime.engine.metrics;

import java.net.URL;

public interface Metrics
{
    String name();

    MetricsContext supply(
        CollectorContext context);

    URL type();
}
