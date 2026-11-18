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
package io.aklivity.zilla.runtime.engine.internal.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;

public class EngineMetricGroupTest
{
    private final EngineMetricGroup group = new EngineMetricGroup(new Configuration());

    @Test
    public void shouldNameEngineMetrics()
    {
        assertThat(group.metricNames(), containsInAnyOrder(
            "engine.workers.usage",
            "engine.workers.capacity",
            "engine.workers.count",
            "engine.workers.listeners.bound",
            "engine.workers.listeners.unbound"));
    }

    @Test
    public void shouldSupplyListenersBoundCounter()
    {
        Metric metric = group.supply("engine.workers.listeners.bound");

        assertThat(metric.name(), equalTo("engine.workers.listeners.bound"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }

    @Test
    public void shouldSupplyListenersUnboundCounter()
    {
        Metric metric = group.supply("engine.workers.listeners.unbound");

        assertThat(metric.name(), equalTo("engine.workers.listeners.unbound"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
    }
}
