/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.metrics;

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.metrics.TestCounterMetric;
import io.aklivity.zilla.runtime.engine.metrics.TestGaugeMetric;
import io.aklivity.zilla.runtime.engine.metrics.TestHistogramMetric;

public final class TestMetricGroup implements MetricGroup
{
    private final Map<String, Supplier<Metric>> testMetrics = Map.of(
        "test.counter", TestCounterMetric::new,
        "test.gauge", TestGaugeMetric::new,
        "test.histogram", TestHistogramMetric::new
    );

    public static final String NAME = "test";

    public TestMetricGroup(
        Configuration config)
    {
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public Metric supply(
        String name)
    {
        return testMetrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return testMetrics.keySet();
    }
}
