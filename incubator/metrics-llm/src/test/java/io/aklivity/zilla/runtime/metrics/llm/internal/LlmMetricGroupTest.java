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
package io.aklivity.zilla.runtime.metrics.llm.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class LlmMetricGroupTest
{
    private final MetricGroup metricGroup = new LlmMetricGroup(new Configuration());

    @Test
    public void shouldReturnMetricNames()
    {
        assertThat(metricGroup.name(), equalTo("llm"));
        assertThat(metricGroup.metricNames(), contains(
            "llm.tokens.input",
            "llm.tokens.output",
            "llm.tokens.total",
            "llm.tokens.cache.read",
            "llm.tokens.cache.write",
            "llm.tokens.reasoning",
            "llm.duration",
            "llm.active.requests"));
    }

    @Test
    public void shouldResolveUnknownMetricAsNull()
    {
        assertThat(metricGroup.supply("llm.unknown"), nullValue());
    }

    @Test
    public void shouldResolveTokensMetrics()
    {
        assertMetric("llm.tokens.input", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM input tokens per exchange");
        assertMetric("llm.tokens.output", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM output tokens per exchange");
        assertMetric("llm.tokens.total", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM total tokens per exchange");
        assertMetric("llm.tokens.cache.read", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM cache read input tokens per exchange");
        assertMetric("llm.tokens.cache.write", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM cache write input tokens per exchange");
        assertMetric("llm.tokens.reasoning", Metric.Kind.HISTOGRAM, Metric.Unit.COUNT,
            "Number of LLM reasoning output tokens per exchange");
    }

    @Test
    public void shouldResolveDurationMetric()
    {
        assertMetric("llm.duration", Metric.Kind.HISTOGRAM, Metric.Unit.NANOSECONDS,
            "Duration of LLM exchanges");
    }

    @Test
    public void shouldResolveActiveRequestsMetric()
    {
        assertMetric("llm.active.requests", Metric.Kind.GAUGE, Metric.Unit.COUNT,
            "Number of active LLM requests");
    }

    private void assertMetric(
        String name,
        Metric.Kind kind,
        Metric.Unit unit,
        String description)
    {
        Metric metric = metricGroup.supply(name);

        assertThat(metric.name(), equalTo(name));
        assertThat(metric.kind(), equalTo(kind));
        assertThat(metric.unit(), equalTo(unit));
        assertThat(metric.description(), equalTo(description));

        MetricContext context = metric.supply(mock(EngineContext.class));

        assertThat(context.group(), equalTo("llm"));
        assertThat(context.kind(), equalTo(kind));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
        assertThat(context.supply(value -> {}), notNullValue());
    }
}
