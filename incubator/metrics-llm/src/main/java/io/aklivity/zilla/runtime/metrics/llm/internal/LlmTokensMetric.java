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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;

public final class LlmTokensMetric implements Metric
{
    private static final String GROUP = LlmMetricGroup.NAME;

    private final LlmTokens tokens;

    LlmTokensMetric(
        LlmTokens tokens)
    {
        this.tokens = tokens;
    }

    static String name(
        LlmTokens tokens)
    {
        return String.format("%s.tokens.%s", GROUP, tokens.segment());
    }

    @Override
    public String name()
    {
        return name(tokens);
    }

    @Override
    public Kind kind()
    {
        return Kind.HISTOGRAM;
    }

    @Override
    public Unit unit()
    {
        return Unit.COUNT;
    }

    @Override
    public String description()
    {
        return String.format("Number of LLM %s tokens per exchange", tokens.summary());
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new LlmTokensMetricContext(GROUP, kind(), tokens, context.supplyTypeId(GROUP));
    }
}
