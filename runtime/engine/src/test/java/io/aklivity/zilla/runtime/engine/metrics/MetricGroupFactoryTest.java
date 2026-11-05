/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.metrics;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestExtCounterMetric;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetricGroup;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetricGroupExtSpi;
import io.aklivity.zilla.runtime.engine.test.internal.metrics.TestMetricGroupFactorySpi;

public final class MetricGroupFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        MetricGroupFactory factory = MetricGroupFactory.instantiate();
        MetricGroup metricGroup = factory.create("test", config);

        assertThat(metricGroup.name(), equalTo(TestMetricGroup.NAME));
    }

    @Test
    public void shouldExtendGroupWithContributedMetric()
    {
        Configuration config = new Configuration();
        MetricGroupFactory factory = MetricGroupFactory.instantiate();
        MetricGroup metricGroup = factory.create("test", config);

        Collection<String> metricNames = metricGroup.metricNames();

        assertThat(metricGroup.name(), equalTo("test"));
        assertThat(metricNames, hasItems("test.counter", "test.gauge", "test.histogram", "test.ext.counter"));
        assertThat(metricGroup.supply("test.ext.counter"), instanceOf(TestExtCounterMetric.class));
        assertThat(metricGroup.supply("test.counter").name(), equalTo("test.counter"));
        assertThat(metricGroup.supply("test.unknown"), nullValue());
    }

    @Test
    public void shouldRejectContributionRedefiningGroupMetric()
    {
        MetricGroupExtSpi redefine = new TestMetricGroupExtSpi()
        {
            @Override
            public Collection<String> metricNames()
            {
                return List.of("test.counter");
            }
        };
        MetricGroupFactory factory = MetricGroupFactory.instantiate(
            List.of(new TestMetricGroupFactorySpi()), List.of(redefine));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> factory.create("test", new Configuration()));

        assertThat(ex.getMessage(), containsString("test.counter"));
        assertThat(ex.getMessage(), containsString("test"));
    }

    @Test
    public void shouldRejectContributionsRedefiningEachOther()
    {
        MetricGroupFactory factory = MetricGroupFactory.instantiate(
            List.of(new TestMetricGroupFactorySpi()),
            List.of(new TestMetricGroupExtSpi(), new TestMetricGroupExtSpi()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> factory.create("test", new Configuration()));

        assertThat(ex.getMessage(), containsString("test.ext.counter"));
    }
}
