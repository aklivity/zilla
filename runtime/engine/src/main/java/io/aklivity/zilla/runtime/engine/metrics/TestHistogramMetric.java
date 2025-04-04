/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.metrics;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Unit.BYTES;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public class TestHistogramMetric implements Metric
{
    private static final String GROUP = "test";
    private static final String NAME = GROUP + ".histogram";
    private static final String DESCRIPTION = "Description for test.histogram";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return HISTOGRAM;
    }

    @Override
    public Unit unit()
    {
        return BYTES;
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
        return new MetricContext()
        {
            @Override
            public String group()
            {
                return GROUP;
            }

            @Override
            public Kind kind()
            {
                return HISTOGRAM;
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
                return MessageConsumer.NOOP;
            }
        };
    }
}
