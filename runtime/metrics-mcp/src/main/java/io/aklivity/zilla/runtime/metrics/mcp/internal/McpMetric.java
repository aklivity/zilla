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

import static io.aklivity.zilla.runtime.metrics.mcp.internal.McpMeasure.DURATION;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;

public final class McpMetric implements Metric
{
    private static final String GROUP = McpMetricGroup.NAME;

    private final McpMethod method;
    private final McpMeasure measure;

    McpMetric(
        McpMethod method,
        McpMeasure measure)
    {
        this.method = method;
        this.measure = measure;
    }

    static String name(
        McpMethod method,
        McpMeasure measure)
    {
        return measure == DURATION
            ? String.format("%s.%s.duration", GROUP, method.segment())
            : String.format("%s.%s", GROUP, method.segment());
    }

    @Override
    public String name()
    {
        return name(method, measure);
    }

    @Override
    public Kind kind()
    {
        return measure == DURATION ? Kind.HISTOGRAM : Kind.COUNTER;
    }

    @Override
    public Unit unit()
    {
        return measure == DURATION ? Unit.NANOSECONDS : Unit.COUNT;
    }

    @Override
    public String description()
    {
        return measure == DURATION
            ? String.format("Duration of MCP %s", method.summary())
            : String.format("MCP %s", method.summary());
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new McpMetricContext(GROUP, name(), kind(), method, measure, context::supplyTypeId);
    }
}
