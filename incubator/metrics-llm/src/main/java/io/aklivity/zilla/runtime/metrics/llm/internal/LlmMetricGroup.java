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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class LlmMetricGroup implements MetricGroup
{
    public static final String NAME = "llm";

    private final Map<String, Supplier<Metric>> llmMetrics;

    public LlmMetricGroup(
        Configuration config)
    {
        Map<String, Supplier<Metric>> metrics = new LinkedHashMap<>();
        for (LlmTokens tokens : LlmTokens.values())
        {
            metrics.put(LlmTokensMetric.name(tokens), () -> new LlmTokensMetric(tokens));
        }
        metrics.put(LlmDurationMetric.NAME, LlmDurationMetric::new);
        metrics.put(LlmActiveRequestsMetric.NAME, LlmActiveRequestsMetric::new);
        this.llmMetrics = metrics;
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
        return llmMetrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return llmMetrics.keySet();
    }
}
