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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.FrameFW;

public class StreamOpensReceivedMetric implements Metric
{
    private static final String GROUP = StreamMetricGroup.NAME;
    private static final String NAME = String.format("%s.%s", GROUP, "opens.received");

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
        return new StreamOpensReceivedMetricContext();
    }

    private final class StreamOpensReceivedMetricContext implements MetricContext
    {
        private final FrameFW frameRO = new FrameFW();

        @Override
        public String group()
        {
            return GROUP;
        }

        @Override
        public Metric.Kind kind()
        {
            return StreamOpensReceivedMetric.this.kind();
        }

        @Override
        public MessageConsumer supply(
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
            if (msgTypeId == BeginFW.TYPE_ID) // opening frame
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
}
