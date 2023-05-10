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
package io.aklivity.zilla.runtime.metrics.grpc.internal;

import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.metrics.grpc.internal.GrpcUtils.initialId;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.ResetFW;

public final class GrpcActiveRequestsMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final FrameFW frameRO = new FrameFW();

    public GrpcActiveRequestsMetricContext(
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
        return new GrpcActiveRequestsMetricHandler(recorder);
    }

    private final class GrpcActiveRequestsMetricHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;
        private static final long EXCHANGE_CLOSED = 0b11L;

        private final LongConsumer recorder;
        private final Long2LongHashMap exchanges;

        private GrpcActiveRequestsMetricHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
            this.exchanges = new Long2LongHashMap(INITIAL_VALUE);
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
            final long exchangeId = initialId(streamId);
            long direction = GrpcUtils.direction(streamId);
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                if (direction == 1L) //received
                {
                    recorder.accept(1L);
                }
                break;
            case ResetFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case EndFW.TYPE_ID:
                final long mask = 1L << direction;
                final long status = exchanges.get(exchangeId) | mask; // mark current direction as closed
                if (status == EXCHANGE_CLOSED) // both received and sent streams are closed
                {
                    exchanges.remove(exchangeId);
                    recorder.accept(-1L);
                }
                else
                {
                    exchanges.put(exchangeId, status);
                }
                break;
            }
        }
    }
}
