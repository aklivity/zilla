/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static io.aklivity.zilla.runtime.metrics.mcp.internal.McpMeasure.COUNT;
import static io.aklivity.zilla.runtime.metrics.mcp.internal.McpMeasure.DURATION;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class McpMetricGroup implements MetricGroup
{
    public static final String NAME = "mcp";

    private final Map<String, Supplier<Metric>> mcpMetrics;

    public McpMetricGroup(
        Configuration config)
    {
        Map<String, Supplier<Metric>> metrics = new LinkedHashMap<>();
        for (McpMethod method : McpMethod.values())
        {
            metrics.put(McpMetric.name(method, COUNT), () -> new McpMetric(method, COUNT));
            metrics.put(McpMetric.name(method, DURATION), () -> new McpMetric(method, DURATION));
        }
        this.mcpMetrics = metrics;
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/mcp.schema.patch.json");
    }

    @Override
    public Metric supply(
        String name)
    {
        return mcpMetrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return mcpMetrics.keySet();
    }
}
