package io.aklivity.zilla.runtime.engine.metrics;

import java.net.URL;
import java.util.Collection;

public interface MetricGroup
{
    String name();

    URL type();

    Metric supply(
        String name);

    Collection<String> metricNames();
}
