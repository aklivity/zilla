/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class EngineMetricGroup implements MetricGroup
{
    public static final String NAME = "engine";

    private final Map<String, Supplier<Metric>> engineMetrics = Map.of(
        EngineWorkerUtilizationMetric.NAME, EngineWorkerUtilizationMetric::new,
        EngineWorkerCapacityMetric.NAME, EngineWorkerCapacityMetric::new,
        EngineWorkerCountMetric.NAME, EngineWorkerCountMetric::new
    );

    public EngineMetricGroup(
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
        return getClass().getResource("schema/engine.schema.patch.json");
    }

    @Override
    public Metric supply(
        String name)
    {
        return engineMetrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return engineMetrics.keySet();
    }
}
