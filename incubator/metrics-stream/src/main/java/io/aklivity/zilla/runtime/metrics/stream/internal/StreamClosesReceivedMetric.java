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

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.FrameFW;

public class StreamClosesReceivedMetric implements Metric
{
    private static final String NAME = String.format("%s.%s", StreamMetricGroup.NAME, "closes.received");

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.COUNTER;
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
        return new StreamClosesReceivedMetricContext();
    }

    private final class StreamClosesReceivedMetricContext implements MetricContext
    {
        private final FrameFW frameRO = new FrameFW();

        @Override
        public Metric metric()
        {
            return StreamClosesReceivedMetric.this;
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
            if (msgTypeId == EndFW.TYPE_ID) // closing frame
            {
                FrameFW frame = frameRO.wrap(buffer, index, index + length);
                long streamId = frame.streamId();
                if (isInitial(streamId)) // received stream
                {
                    recorder.accept(1L);
                }
            }
        }
    }

    private static boolean isInitial(
        long streamId)
    {
        return (streamId & 0x0000_0000_0000_0001L) != 0L;
    }
}
