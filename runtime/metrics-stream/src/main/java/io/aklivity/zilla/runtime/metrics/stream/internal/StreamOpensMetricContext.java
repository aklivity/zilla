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
package io.aklivity.zilla.runtime.metrics.stream.internal;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.BeginFW;

public final class StreamOpensMetricContext implements MetricContext
{
    private final String group;
    private final Metric.Kind kind;
    private final Direction direction;

    public StreamOpensMetricContext(
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
        return new StreamOpensHandler(recorder);
    }

    private final class StreamOpensHandler implements MessageConsumer
    {
        private final LongConsumer recorder;

        private StreamOpensHandler(LongConsumer recorder)
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
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                recorder.accept(1L);
            }
        }
    }
}
