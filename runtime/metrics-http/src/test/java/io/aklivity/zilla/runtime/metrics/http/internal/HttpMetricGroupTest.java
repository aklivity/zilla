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
package io.aklivity.zilla.runtime.metrics.http.internal;

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

public class HttpMetricGroupTest
{
    @Test
    public void shouldReturnMetricNames()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Collection<String> metricNames = metricGroup.metricNames();

        // THEN
        assertThat(metricNames, containsInAnyOrder(
            "http.request.size",
            "http.response.size",
            "http.active.requests",
            "http.duration"
        ));
    }

    @Test
    public void shouldResolveHttpRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.request.size");

        // THEN
        assertThat(metric, instanceOf(HttpRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("http.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("HTTP request content length"));
    }

    @Test
    public void shouldResolveHttpRequestSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.request.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpSizeMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveHttpResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.response.size");

        // THEN
        assertThat(metric, instanceOf(HttpResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("http.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("HTTP response content length"));
    }

    @Test
    public void shouldResolveHttpResponseSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.response.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpSizeMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveHttpActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.active.requests");

        // THEN
        assertThat(metric, instanceOf(HttpActiveRequestsMetric.class));
        assertThat(metric.name(), equalTo("http.active.requests"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of active HTTP requests"));
    }

    @Test
    public void shouldResolveHttpActiveRequestsContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.active.requests");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpActiveRequestsMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldResolveHttpDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("http.duration");

        // THEN
        assertThat(metric, instanceOf(HttpDurationMetric.class));
        assertThat(metric.name(), equalTo("http.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.NANOSECONDS));
        assertThat(metric.description(), equalTo("Duration of HTTP requests"));
    }

    @Test
    public void shouldResolveHttpDurationContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new HttpMetricGroup(config);
        Metric metric = metricGroup.supply("http.duration");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(HttpDurationMetricContext.class));
        assertThat(context.group(), equalTo("http"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }
}
