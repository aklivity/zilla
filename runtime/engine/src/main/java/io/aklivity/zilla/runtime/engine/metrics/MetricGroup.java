package io.aklivity.zilla.runtime.engine.metrics;

import java.net.URL;

public interface MetricGroup
{
    String name();

    URL type();

    Metric resolve(
        String name);
}
