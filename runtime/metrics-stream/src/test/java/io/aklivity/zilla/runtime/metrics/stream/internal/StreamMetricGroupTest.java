/*
 * Copyright 2021-2026 Aklivity Inc
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class StreamMetricGroupTest
{
    @Test
    public void shouldReturnMetricNames()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Collection<String> metricNames = metricGroup.metricNames();

        // THEN
        assertThat(metricNames, containsInAnyOrder(
            "stream.opens.received", "stream.opens.sent",
            "stream.data.received", "stream.data.sent",
            "stream.errors.received", "stream.errors.sent",
            "stream.closes.received", "stream.closes.sent",
            "stream.active.received", "stream.active.sent"
        ));
    }

    @Test
    public void shouldResolveStreamOpensReceived()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.opens.received");

        // THEN
        assertThat(metric, instanceOf(StreamOpensReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.opens.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of opened received streams"));
    }

    @Test
    public void shouldResolveStreamOpensReceivedContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.opens.received");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamOpensMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveStreamOpensSent()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.opens.sent");

        // THEN
        assertThat(metric, instanceOf(StreamOpensSentMetric.class));
        assertThat(metric.name(), equalTo("stream.opens.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of opened sent streams"));
    }

    @Test
    public void shouldResolveStreamOpensSentContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.opens.sent");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamOpensMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveStreamDataReceived()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.data.received");

        // THEN
        assertThat(metric, instanceOf(StreamDataReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.data.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("Bytes of data on received streams"));
    }

    @Test
    public void shouldResolveStreamDataReceivedContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.data.received");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamDataMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveStreamDataSent()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.data.sent");

        // THEN
        assertThat(metric, instanceOf(StreamDataSentMetric.class));
        assertThat(metric.name(), equalTo("stream.data.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("Bytes of data on sent streams"));
    }

    @Test
    public void shouldResolveStreamDataSentContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.data.sent");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamDataMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveStreamErrorsReceived()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.errors.received");

        // THEN
        assertThat(metric, instanceOf(StreamErrorsReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.errors.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of errors on received streams"));
    }

    @Test
    public void shouldResolveStreamErrorsReceivedContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.errors.received");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamErrorsMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveStreamErrorsSent()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.errors.sent");

        // THEN
        assertThat(metric, instanceOf(StreamErrorsSentMetric.class));
        assertThat(metric.name(), equalTo("stream.errors.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of errors on sent streams"));
    }

    @Test
    public void shouldResolveStreamErrorsSentContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.errors.sent");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamErrorsMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveStreamClosesReceived()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.closes.received");

        // THEN
        assertThat(metric, instanceOf(StreamClosesReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.closes.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of closed received streams"));
    }

    @Test
    public void shouldResolveStreamClosesReceivedContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.closes.received");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamClosesMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveStreamClosesSent()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.closes.sent");

        // THEN
        assertThat(metric, instanceOf(StreamClosesSentMetric.class));
        assertThat(metric.name(), equalTo("stream.closes.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of closed sent streams"));
    }

    @Test
    public void shouldResolveStreamClosesSentContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.closes.sent");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamClosesMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveStreamActiveReceived()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.active.received");

        // THEN
        assertThat(metric, instanceOf(StreamActiveReceivedMetric.class));
        assertThat(metric.name(), equalTo("stream.active.received"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of currently active received streams"));
    }

    @Test
    public void shouldResolveStreamActiveReceivedContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.active.received");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamActiveMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveStreamActiveSent()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("stream.active.sent");

        // THEN
        assertThat(metric, instanceOf(StreamActiveSentMetric.class));
        assertThat(metric.name(), equalTo("stream.active.sent"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of currently active sent streams"));
    }

    @Test
    public void shouldResolveStreamActiveSentContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new StreamMetricGroup(config);
        Metric metric = metricGroup.supply("stream.active.sent");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(StreamActiveMetricContext.class));
        assertThat(context.group(), equalTo("stream"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }
}
