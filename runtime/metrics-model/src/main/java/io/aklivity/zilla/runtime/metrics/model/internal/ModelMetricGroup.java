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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public final class ModelMetricGroup implements MetricGroup
{
    public static final String NAME = "model";

    private final Map<String, Supplier<Metric>> metrics;

    public ModelMetricGroup(
        Configuration config)
    {
        Map<String, Supplier<Metric>> metrics = new LinkedHashMap<>();
        metrics.put(ModelMetric.FIELDS_REPLACED, () -> new ModelMetric(
            ModelMetric.FIELDS_REPLACED, Metric.Kind.COUNTER, Metric.Unit.COUNT,
            "Number of fields replaced by a model transform"));
        metrics.put(ModelMetric.FIELDS_DECLINED, () -> new ModelMetric(
            ModelMetric.FIELDS_DECLINED, Metric.Kind.COUNTER, Metric.Unit.COUNT,
            "Number of fields declined by a model transform"));
        metrics.put(ModelMetric.REJECTED_INVALID, () -> new ModelMetric(
            ModelMetric.REJECTED_INVALID, Metric.Kind.COUNTER, Metric.Unit.COUNT,
            "Number of values rejected as invalid by a model"));
        metrics.put(ModelMetric.REJECTED_WITHHELD, () -> new ModelMetric(
            ModelMetric.REJECTED_WITHHELD, Metric.Kind.COUNTER, Metric.Unit.COUNT,
            "Number of values withheld by a model"));
        metrics.put(ModelMetric.TRANSFORM_DURATION, () -> new ModelMetric(
            ModelMetric.TRANSFORM_DURATION, Metric.Kind.HISTOGRAM, Metric.Unit.NANOSECONDS,
            "Duration of a model transform, including time suspended on asynchronous work"));
        this.metrics = metrics;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Metric supply(
        String name)
    {
        return metrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return metrics.keySet();
    }
}
