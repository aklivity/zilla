package io.aklivity.zilla.runtime.engine.metrics;

public class TestCounterMetric implements Metric
{
    private static final String NAME = "test.counter";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.COUNTER;
    }

    @Override
    public Unit unit()
    {
        return Unit.COUNT;
    }

    @Override
    public MetricHandler supplyReceived(
        long bindingId)
    {
        return new TestMetricHandler(NAME, MetricHandler.Event.RECEIVED, bindingId);
    }

    @Override
    public MetricHandler supplySent(
        long bindingId)
    {
        return new TestMetricHandler(NAME, MetricHandler.Event.SENT, bindingId);
    }
}
