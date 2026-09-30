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

import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmTokens.ABSENT;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmUsageFW;

public final class LlmTokensMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final LlmTokens tokens;
    private final int llmTypeId;

    LlmTokensMetricContext(
        String group,
        Metric.Kind kind,
        LlmTokens tokens,
        int llmTypeId)
    {
        this.group = group;
        this.kind = kind;
        this.tokens = tokens;
        this.llmTypeId = llmTypeId;
    }

    @Override
    public String group()
    {
        return group;
    }

    @Override
    public Metric.Kind kind()
    {
        return kind;
    }

    @Override
    public Direction direction()
    {
        return BOTH;
    }

    @Override
    public MessageConsumer supply(
        LongConsumer recorder)
    {
        return new LlmTokensHandler(recorder);
    }

    private final class LlmTokensHandler implements MessageConsumer
    {
        private final LongConsumer recorder;
        private final EndFW endRO = new EndFW();
        private final AbortFW abortRO = new AbortFW();
        private final LlmEndExFW llmEndExRO = new LlmEndExFW();
        private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();

        private LlmTokensHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                final LlmEndExFW llmEndEx = end.extension().get(llmEndExRO::tryWrap);
                if (llmEndEx != null && llmEndEx.typeId() == llmTypeId)
                {
                    onUsage(llmEndEx.usage());
                }
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                final LlmAbortExFW llmAbortEx = abort.extension().get(llmAbortExRO::tryWrap);
                if (llmAbortEx != null && llmAbortEx.typeId() == llmTypeId)
                {
                    onUsage(llmAbortEx.usage());
                }
                break;
            }
        }

        private void onUsage(
            LlmUsageFW usage)
        {
            final int count = tokens.count(usage);
            if (count > ABSENT)
            {
                recorder.accept(count);
            }
        }
    }
}
