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
package io.aklivity.zilla.runtime.metrics.model.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class ModelMetricGroupTest
{
    private final MetricGroup metricGroup = new ModelMetricGroup(new Configuration());

    @Test
    public void shouldReturnMetricNames()
    {
        assertThat(metricGroup.name(), equalTo("model"));
        assertThat(metricGroup.metricNames(), contains(
            "model.fields.replaced",
            "model.fields.declined",
            "model.rejected.invalid",
            "model.rejected.withheld",
            "model.transform.duration"));
    }

    @Test
    public void shouldResolveUnknownMetricAsNull()
    {
        assertThat(metricGroup.supply("model.unknown"), nullValue());
    }

    @Test
    public void shouldResolveCounters()
    {
        assertMetric("model.fields.replaced", Metric.Kind.COUNTER, Metric.Unit.COUNT);
        assertMetric("model.fields.declined", Metric.Kind.COUNTER, Metric.Unit.COUNT);
        assertMetric("model.rejected.invalid", Metric.Kind.COUNTER, Metric.Unit.COUNT);
        assertMetric("model.rejected.withheld", Metric.Kind.COUNTER, Metric.Unit.COUNT);
    }

    @Test
    public void shouldResolveTransformDuration()
    {
        assertMetric("model.transform.duration", Metric.Kind.HISTOGRAM, Metric.Unit.NANOSECONDS);
    }

    @Test
    public void shouldNotDeriveMetricsFromStreamFrames()
    {
        for (String name : metricGroup.metricNames())
        {
            MetricContext context = metricGroup.supply(name).supply(null);

            assertThat(name, context.direction(), equalTo(MetricContext.Direction.NONE));
            assertThat(name, context.group(), equalTo("model"));
        }
    }

    private void assertMetric(
        String name,
        Metric.Kind kind,
        Metric.Unit unit)
    {
        Metric metric = metricGroup.supply(name);

        assertThat(metric.name(), equalTo(name));
        assertThat(metric.kind(), equalTo(kind));
        assertThat(metric.unit(), equalTo(unit));
    }
}
