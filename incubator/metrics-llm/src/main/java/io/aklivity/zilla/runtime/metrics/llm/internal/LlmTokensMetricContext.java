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
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.RECEIVED;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.SENT;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.initialId;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmAbortExFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmEndExFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmUsageFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.ResetFW;

public final class LlmTokensMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final LlmTokens tokens;
    private final EngineContext context;
    private final int llmTypeId;

    LlmTokensMetricContext(
        String group,
        Metric.Kind kind,
        LlmTokens tokens,
        EngineContext context)
    {
        this.group = group;
        this.kind = kind;
        this.tokens = tokens;
        this.context = context;
        this.llmTypeId = context.supplyTypeId(group);
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
        return new LlmTokensHandler(attributesId -> recorder, LlmAttributes.NONE);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes,
        ToLongFunction<String> resolveId)
    {
        return new LlmTokensHandler(recorder, new LlmAttributes(attributes, context, resolveId));
    }

    private final class LlmTokensHandler implements MessageConsumer
    {
        private final IntFunction<LongConsumer> recorder;
        private final LlmAttributes attributes;
        private final FrameFW frameRO = new FrameFW();
        private final BeginFW beginRO = new BeginFW();
        private final EndFW endRO = new EndFW();
        private final AbortFW abortRO = new AbortFW();
        private final ExtensionFW extensionRO = new ExtensionFW();
        private final LlmEndExFW llmEndExRO = new LlmEndExFW();
        private final LlmAbortExFW llmAbortExRO = new LlmAbortExFW();

        private LlmTokensHandler(
            IntFunction<LongConsumer> recorder,
            LlmAttributes attributes)
        {
            this.recorder = recorder;
            this.attributes = attributes;
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            final long exchangeId = initialId(streamId);
            final long direction = LlmUtils.direction(streamId);

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final ExtensionFW beginEx = begin.extension().get(extensionRO::tryWrap);
                if (direction == RECEIVED && beginEx != null && beginEx.typeId() == llmTypeId)
                {
                    attributes.request(exchangeId, frame.authorization());
                }
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                final LlmEndExFW llmEndEx = end.extension().get(llmEndExRO::tryWrap);
                if (llmEndEx != null && llmEndEx.typeId() == llmTypeId)
                {
                    onUsage(exchangeId, llmEndEx.usage());
                }
                if (direction == SENT)
                {
                    attributes.release(exchangeId);
                }
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                final LlmAbortExFW llmAbortEx = abort.extension().get(llmAbortExRO::tryWrap);
                if (llmAbortEx != null && llmAbortEx.typeId() == llmTypeId)
                {
                    onUsage(exchangeId, llmAbortEx.usage());
                }
                attributes.release(exchangeId);
                break;
            case ResetFW.TYPE_ID:
                attributes.release(exchangeId);
                break;
            }
        }

        private void onUsage(
            long exchangeId,
            LlmUsageFW usage)
        {
            final int count = tokens.count(usage);
            if (count > ABSENT)
            {
                recorder.apply(attributes.attributesId(exchangeId)).accept(count);
            }
        }
    }
}
