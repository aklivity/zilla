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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.metrics.stream.internal.types.stream.FrameFW;

public class StreamErrorsReceivedMetric implements Metric
{
    private static final String GROUP = StreamMetricGroup.NAME;
    private static final String NAME = String.format("%s.%s", GROUP, "errors.received");
    private static final String DESCRIPTION = "Number of errors on received streams";

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
    public StreamDirection streamDirection()
    {
        return StreamDirection.RECEIVED;
    }

    @Override
    public String description()
    {
        return DESCRIPTION;
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new StreamErrorsReceivedMetricContext();
    }

    private final class StreamErrorsReceivedMetricContext implements MetricContext
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
            return StreamErrorsReceivedMetric.this.kind();
        }

        @Override
        public StreamDirection streamDirection()
        {
            return StreamErrorsReceivedMetric.this.streamDirection();
        }

        @Override
        public MessageConsumer supply(
            LongConsumer recorder)
        {
            return new StreamErrorsHandler(recorder);
        }
    }
}
