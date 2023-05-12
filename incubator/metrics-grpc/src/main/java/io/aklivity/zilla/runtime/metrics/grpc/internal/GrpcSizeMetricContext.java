/*
 * Copyright 2021-2023 Aklivity Inc
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

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2LongCounterMap;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.metrics.grpc.internal.types.stream.ResetFW;

public final class GrpcSizeMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final Direction direction;
    private final FrameFW frameRO = new FrameFW();
    private final DataFW dataRO = new DataFW();

    public GrpcSizeMetricContext(
        String group,
        Metric.Kind kind,
        Direction direction)
    {
        this.group = group;
        this.kind = kind;
        this.direction = direction;
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
        return direction;
    }

    @Override
    public MessageConsumer supply(
        LongConsumer recorder)
    {
        return new GrpcSizeHandler(recorder);
    }

    private final class GrpcSizeHandler implements MessageConsumer
    {
        private static final long INITIAL_VALUE = 0L;

        private final LongConsumer recorder;
        private final Long2LongCounterMap size = new Long2LongCounterMap(INITIAL_VALUE);

        private GrpcSizeHandler(
            LongConsumer recorder)
        {
            this.recorder = recorder;
        }

        public void accept(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long streamId = frame.streamId();
            switch (msgTypeId)
            {
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                size.getAndAdd(streamId, data.length());
                break;
            case EndFW.TYPE_ID:
                recorder.accept(size.remove(streamId));
                break;
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                size.remove(streamId);
                break;
            }
        }
    }
}
