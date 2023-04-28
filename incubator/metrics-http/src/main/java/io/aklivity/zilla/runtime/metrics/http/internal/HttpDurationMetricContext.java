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
package io.aklivity.zilla.runtime.metrics.http.internal;

import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.commonId;
import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.streamDirection;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

public final class HttpDurationMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final FrameFW frameRO = new FrameFW();

    public HttpDurationMetricContext(
        String group,
        Metric.Kind kind)
    {
        this.group = group;
        this.kind = kind;
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
        return new HttpActiveRequestsMetricHandler(recorder);
    }

    private final class HttpActiveRequestsMetricHandler implements MessageConsumer
    {
        private static final long INITIAL_STATUS = 0b11L;

        private final LongConsumer recorder;
        private final Long2LongHashMap streams = new Long2LongHashMap(0L);
        private final Long2LongHashMap timestamps = new Long2LongHashMap(0L);

        private HttpActiveRequestsMetricHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
        }

        @Override
        public void accept(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            final long commonId = commonId(streamId);
            long direction = streamDirection(streamId);
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                if (direction == 1L) //received
                {
                    streams.put(commonId, INITIAL_STATUS);
                    timestamps.put(commonId, System.currentTimeMillis());
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case EndFW.TYPE_ID:
                long status = streams.get(commonId);
                status = status & direction; // mark current direction as closed
                if (status == 0L) // both received and sent streams are closed
                {
                    streams.remove(commonId);
                    long start = timestamps.remove(commonId);
                    long duration = (System.currentTimeMillis() - start) / 1000L;
                    recorder.accept(duration);
                }
                else
                {
                    streams.put(commonId, status);
                }
                break;
            }
        }
    }
}
