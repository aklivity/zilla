package io.aklivity.zilla.runtime.engine.metrics;

import io.aklivity.zilla.runtime.engine.EngineContext;

public interface MetricContext
{
    MetricHandler supply(
        EngineContext context);
}
