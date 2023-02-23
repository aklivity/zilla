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
package io.aklivity.zilla.runtime.metrics.http.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.CollectorContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.Metrics;
import io.aklivity.zilla.runtime.engine.metrics.MetricsContext;

public class HttpMetricsTest
{
    @Test
    public void shouldResolveHttpRequestSize()
    {
        Configuration config = new Configuration();
        Metrics metrics = new HttpMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("http.request.size");

        assertThat(metric, instanceOf(HttpRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("http.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.supply(null), nullValue());
    }

    @Test
    public void shouldResolveHttpResponseSize()
    {
        Configuration config = new Configuration();
        Metrics metrics = new HttpMetrics(config);
        CollectorContext collector = Mockito.mock(CollectorContext.class);
        MetricsContext metricsContext = metrics.supply(collector);
        Metric metric = metricsContext.resolve("http.response.size");

        assertThat(metric, instanceOf(HttpResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("http.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.supply(null), nullValue());
    }
}
