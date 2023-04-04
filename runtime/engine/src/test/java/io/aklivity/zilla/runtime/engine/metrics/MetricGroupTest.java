package io.aklivity.zilla.runtime.engine.metrics;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.LongConsumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetricGroup;

public class MetricGroupTest
{
    @Test
    public void shouldResolveCounter()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("test.counter");
        MetricHandler handler = metric.supply(mock(EngineContext.class)).supply(mock(LongConsumer.class));

        // THEN
        assertThat(metric, instanceOf(TestCounterMetric.class));
        assertThat(metric.name(), equalTo("test.counter"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(handler, instanceOf(MetricHandler.class));
    }

    @Test
    public void shouldResolveGauge()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("test.gauge");
        MetricHandler handler = metric.supply(mock(EngineContext.class)).supply(mock(LongConsumer.class));

        // THEN
        assertThat(metric, instanceOf(TestGaugeMetric.class));
        assertThat(metric.name(), equalTo("test.gauge"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(handler, instanceOf(MetricHandler.class));
    }

    @Test
    public void shouldResolveHistogram()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("test.histogram");
        MetricHandler handler = metric.supply(mock(EngineContext.class)).supply(mock(LongConsumer.class));

        // THEN
        assertThat(metric, instanceOf(TestHistogramMetric.class));
        assertThat(metric.name(), equalTo("test.histogram"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(handler, instanceOf(MetricHandler.class));
    }
}
