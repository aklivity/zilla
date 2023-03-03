/*
 * Copyright 2021-2022 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.metrics.stream.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.CollectorContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.Metrics;
import io.aklivity.zilla.runtime.engine.metrics.MetricsContext;

public class StreamMetricsTest
{
    @Test
    public void shouldResolveStreamOpensReceived()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.opens.received");

        assertThat(metric, instanceOf(StreamOpensReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.opens.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldResolveStreamOpensSent()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.opens.sent");

        assertThat(metric, instanceOf(StreamOpensSentMetric.class));
        assertThat(metric.name(), equalTo("stream.opens.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldResolveStreamDataReceived()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.data.received");

        assertThat(metric, instanceOf(StreamDataReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.data.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
    }

    @Test
    public void shouldResolveStreamDataSent()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.data.sent");

        assertThat(metric, instanceOf(StreamDataSentMetric.class));
        assertThat(metric.name(), equalTo("stream.data.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
    }
    @Test
    public void shouldResolveStreamErrorsReceived()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.errors.received");

        assertThat(metric, instanceOf(StreamErrorsReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.errors.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldResolveStreamErrorsSent()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.errors.sent");

        assertThat(metric, instanceOf(StreamErrorsSentMetric.class));
        assertThat(metric.name(), equalTo("stream.errors.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldResolveStreamClosesReceived()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.closes.received");

        assertThat(metric, instanceOf(StreamClosesReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.closes.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldResolveStreamClosesSent()
    {
        Configuration config = new Configuration();
        Metrics metrics = new StreamMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("stream.closes.sent");

        assertThat(metric, instanceOf(StreamClosesSentMetric.class));
        assertThat(metric.name(), equalTo("stream.closes.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }
}
