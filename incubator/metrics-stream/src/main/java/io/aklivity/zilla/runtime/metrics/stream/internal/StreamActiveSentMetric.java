/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.stream.internal;

import static io.aklivity.zilla.runtime.metrics.stream.internal.StreamUtils.isInitial;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongCounterMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.ResetFW;

public class StreamActiveSentMetric implements Metric
{
    private static final String NAME = String.format("%s.%s", StreamMetricGroup.NAME, "active.sent");

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.GAUGE;
    }

    @Override
    public Unit unit()
    {
        return Unit.COUNT;
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new StreamActiveSentMetricContext();
    }

    private final class StreamActiveSentMetricContext implements MetricContext
    {
        private static final long INITIAL_VALUE = 0L;

        private final Long2LongCounterMap activeStreams = new Long2LongCounterMap(INITIAL_VALUE);
        private final FrameFW frameRO = new FrameFW();

        @Override
        public Kind kind()
        {
            return StreamActiveSentMetric.this.kind();
        }

        @Override
        public MetricHandler supply(
            LongConsumer recorder)
        {
            return (t, b, i, l) -> handle(recorder, t, b, i, l);
        }

        private void handle(
            LongConsumer recorder,
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            if (!isInitial(streamId)) // sent stream
            {
                handleFrame(streamId, msgTypeId);
                recorder.accept(activeStreams.size());
            }
        }

        private void handleFrame(
            long streamId,
            int msgTypeId)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                activeStreams.getAndIncrement(streamId);
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                if (activeStreams.getAndDecrement(streamId) == INITIAL_VALUE)
                {
                    activeStreams.remove(streamId);
                }
                break;
            }
        }
    }
}
