package io.aklivity.zilla.runtime.engine.metrics;

public interface Metric
{
    enum Kind
    {
        COUNTER,
        GAUGE,
        HISTOGRAM
    }

    enum Unit
    {
        BYTES,
        COUNT
    }

    String name();

    Kind kind();

    Unit unit();

    MetricHandler supplyReceived(
        long bindingId);

    MetricHandler supplySent(
        long bindingId);
}
