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
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.RECEIVED;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.SENT;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.initialId;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;

import org.agrona.collections.Long2LongHashMap;

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
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.ResetFW;

public final class LlmActiveRequestsMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final EngineContext context;
    private final int llmTypeId;

    LlmActiveRequestsMetricContext(
        String group,
        Metric.Kind kind,
        EngineContext context)
    {
        this.group = group;
        this.kind = kind;
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
        return new LlmActiveRequestsHandler(attributesId -> recorder, LlmAttributes.NONE);
    }

    @Override
    public MessageConsumer supply(
        IntFunction<LongConsumer> recorder,
        List<AttributeConfig> attributes,
        ToLongFunction<String> resolveId)
    {
        return new LlmActiveRequestsHandler(recorder, new LlmAttributes(attributes, context, resolveId));
    }

    private final class LlmActiveRequestsHandler implements MessageConsumer
    {
        private static final long NOT_TRACKED = -1L;
        private static final long REPLY_CLOSED = 1L << SENT;
        private static final long INITIAL_CLOSED = 1L << RECEIVED;
        private static final long EXCHANGE_CLOSED = INITIAL_CLOSED | REPLY_CLOSED;
        private static final long REPLY_OPENED = 1L << 2;

        private final IntFunction<LongConsumer> recorder;
        private final LlmAttributes attributes;
        private final Long2LongHashMap exchanges;
        private final FrameFW frameRO = new FrameFW();
        private final BeginFW beginRO = new BeginFW();
        private final ExtensionFW extensionRO = new ExtensionFW();

        private LlmActiveRequestsHandler(
            IntFunction<LongConsumer> recorder,
            LlmAttributes attributes)
        {
            this.recorder = recorder;
            this.attributes = attributes;
            this.exchanges = new Long2LongHashMap(NOT_TRACKED);
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
                onBegin(exchangeId, direction, frame.authorization(), begin);
                break;
            case EndFW.TYPE_ID:
                onClose(exchangeId, direction, false);
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                onClose(exchangeId, direction, true);
                break;
            }
        }

        private void onBegin(
            long exchangeId,
            long direction,
            long authorization,
            BeginFW begin)
        {
            if (direction == RECEIVED)
            {
                final ExtensionFW beginEx = begin.extension().get(extensionRO::tryWrap);
                if (beginEx != null && beginEx.typeId() == llmTypeId)
                {
                    exchanges.put(exchangeId, 0L);
                    attributes.request(exchangeId, authorization);
                    recorder.apply(attributes.attributesId(exchangeId)).accept(1L);
                }
            }
            else
            {
                final long state = exchanges.get(exchangeId);
                if (state != NOT_TRACKED)
                {
                    exchanges.put(exchangeId, state | REPLY_OPENED);
                }
            }
        }

        private void onClose(
            long exchangeId,
            long direction,
            boolean failed)
        {
            final long state = exchanges.get(exchangeId);
            if (state != NOT_TRACKED)
            {
                long closed = state | 1L << direction;
                if (failed && direction == RECEIVED && (closed & REPLY_OPENED) == 0L)
                {
                    closed |= REPLY_CLOSED;
                }

                if ((closed & EXCHANGE_CLOSED) == EXCHANGE_CLOSED)
                {
                    exchanges.remove(exchangeId);
                    recorder.apply(attributes.attributesId(exchangeId)).accept(-1L);
                    attributes.release(exchangeId);
                }
                else
                {
                    exchanges.put(exchangeId, closed);
                }
            }
        }
    }
}
