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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.descriptor;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Unit.BYTES;

import java.util.Map;
import java.util.function.Function;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.metrics.Metric;

public class PrometheusMetricDescriptor implements MetricDescriptor
{
    private final Function<String, Metric> metricResolver;
    private final Map<String, String> names;
    private final Map<String, String> kinds;
    private final Map<String, String> descriptions;

    public PrometheusMetricDescriptor(
        Function<String, Metric> metricResolver)
    {
        this.metricResolver = metricResolver;
        this.names = new Object2ObjectHashMap<>();
        this.kinds = new Object2ObjectHashMap<>();
        this.descriptions = new Object2ObjectHashMap<>();
    }

    @Override
    public String kind(
        String internalName)
    {
        String result = kinds.get(internalName);
        if (result == null)
        {
            result = metricResolver.apply(internalName).kind().toString().toLowerCase();
            kinds.put(internalName, result);
        }
        return result;
    }

    @Override
    public String name(
        String internalName)
    {
        String result = names.get(internalName);
        if (result == null)
        {
            Metric metric = metricResolver.apply(internalName);
            result = metric.name();
            result = result.replace('.', '_');
            if (metric.unit() == BYTES)
            {
                result += "_bytes";
            }
            if (metric.kind() == COUNTER)
            {
                result += "_total";
            }
            names.put(internalName, result);
        }
        return result;
    }

    @Override
    public String description(
        String internalName)
    {
        String result = descriptions.get(internalName);
        if (result == null)
        {
            result = metricResolver.apply(internalName).description();
            descriptions.put(internalName, result);
        }
        return result;
    }
}
