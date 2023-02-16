package io.aklivity.zilla.runtime.engine.metrics;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetrics;

public class MetricsTest
{
    @Test
    public void shouldResolveCounter()
    {
        Configuration config = new Configuration();
        Metrics metrics = new TestMetrics(config);
        TestCollectorContext testCollectorContext = new TestCollectorContext();
        MetricsContext metricsContext = metrics.supply(testCollectorContext);
        Metric metric = metricsContext.resolve("test.counter");

        assertThat(metric, instanceOf(TestCounterMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
    }

    @Test
    public void shouldResolveGauge()
    {
        Configuration config = new Configuration();
        Metrics metrics = new TestMetrics(config);
        TestCollectorContext testCollectorContext = new TestCollectorContext();
        MetricsContext metricsContext = metrics.supply(testCollectorContext);
        Metric metric = metricsContext.resolve("test.gauge");

        assertThat(metric, instanceOf(TestGaugeMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
    }

    @Test
    public void shouldResolveHistogram()
    {
        Configuration config = new Configuration();
        Metrics metrics = new TestMetrics(config);
        TestCollectorContext testCollectorContext = new TestCollectorContext();
        MetricsContext metricsContext = metrics.supply(testCollectorContext);
        Metric metric = metricsContext.resolve("test.histogram");

        assertThat(metric, instanceOf(TestHistogramMetric.class));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
    }
}
