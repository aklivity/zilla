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
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.direction;
import static io.aklivity.zilla.runtime.metrics.llm.internal.LlmUtils.initialId;

import java.util.function.LongConsumer;

import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.ResetFW;

public final class LlmDurationMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final int llmTypeId;

    LlmDurationMetricContext(
        String group,
        Metric.Kind kind,
        int llmTypeId)
    {
        this.group = group;
        this.kind = kind;
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
        return new LlmDurationHandler(recorder);
    }

    private final class LlmDurationHandler implements MessageConsumer
    {
        private static final long NOT_STARTED = 0L;

        private final LongConsumer recorder;
        private final Long2LongHashMap timestamps;
        private final FrameFW frameRO = new FrameFW();
        private final BeginFW beginRO = new BeginFW();
        private final ExtensionFW extensionRO = new ExtensionFW();

        private LlmDurationHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
            this.timestamps = new Long2LongHashMap(NOT_STARTED);
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
            final long timestamp = frame.timestamp();

            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                final ExtensionFW beginEx = begin.extension().get(extensionRO::tryWrap);
                if (direction(streamId) == RECEIVED &&
                    beginEx != null && beginEx.typeId() == llmTypeId &&
                    timestamp != NOT_STARTED)
                {
                    timestamps.put(exchangeId, timestamp);
                }
                break;
            case EndFW.TYPE_ID:
                if (direction(streamId) == SENT)
                {
                    final long start = timestamps.remove(exchangeId);
                    if (start != NOT_STARTED)
                    {
                        recorder.accept(timestamp - start);
                    }
                }
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                timestamps.remove(exchangeId);
                break;
            }
        }
    }
}
