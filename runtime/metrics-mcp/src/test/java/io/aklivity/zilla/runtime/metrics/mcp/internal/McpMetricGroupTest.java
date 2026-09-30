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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class McpMetricGroupTest
{
    private final Configuration config = new Configuration();

    @Test
    public void shouldReturnMetricNames()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Collection<String> metricNames = metricGroup.metricNames();

        assertThat(metricNames, containsInAnyOrder(
            "mcp.initialize", "mcp.initialize.duration",
            "mcp.tools.list", "mcp.tools.list.duration",
            "mcp.tools.call", "mcp.tools.call.duration",
            "mcp.resources.list", "mcp.resources.list.duration",
            "mcp.resources.read", "mcp.resources.read.duration",
            "mcp.prompts.list", "mcp.prompts.list.duration",
            "mcp.prompts.get", "mcp.prompts.get.duration"));
    }

    @Test
    public void shouldResolveUnknownMetricAsNull()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        assertThat(metricGroup.supply("mcp.unknown"), equalTo(null));
        assertThat(metricGroup.name(), equalTo("mcp"));
    }

    @Test
    public void shouldResolveToolsCallCounter()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Metric metric = metricGroup.supply("mcp.tools.call");

        assertThat(metric, instanceOf(McpMetric.class));
        assertThat(metric.name(), equalTo("mcp.tools.call"));
        assertThat(metric.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("MCP tool invocations"));
    }

    @Test
    public void shouldResolveToolsCallDuration()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);

        Metric metric = metricGroup.supply("mcp.tools.call.duration");

        assertThat(metric.name(), equalTo("mcp.tools.call.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.NANOSECONDS));
        assertThat(metric.description(), equalTo("Duration of MCP tool invocations"));
    }

    @Test
    public void shouldResolveContext()
    {
        MetricGroup metricGroup = new McpMetricGroup(config);
        Metric metric = metricGroup.supply("mcp.tools.call");

        MetricContext context = metric.supply(mock(EngineContext.class));

        assertThat(context.group(), equalTo("mcp"));
        assertThat(context.kind(), equalTo(Metric.Kind.COUNTER));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
        assertThat(((McpMetricContext) context).name(), equalTo("mcp.tools.call"));
    }
}
