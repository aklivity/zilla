package io.aklivity.zilla.runtime.engine.metrics;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetricGroup;

public class MetricGroupTest
{
    @Test
    public void shouldResolveCounter()
    {
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);
        Metric metric = metricGroup.resolve("test.counter");

        assertThat(metric, instanceOf(TestCounterMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
    }

    @Test
    public void shouldResolveGauge()
    {
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);
        Metric metric = metricGroup.resolve("test.gauge");

        assertThat(metric, instanceOf(TestGaugeMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
    }

    @Test
    public void shouldResolveHistogram()
    {
        Configuration config = new Configuration();
        MetricGroup metricGroup = new TestMetricGroup(config);
        Metric metric = metricGroup.resolve("test.histogram");

        assertThat(metric, instanceOf(TestHistogramMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
    }
}
