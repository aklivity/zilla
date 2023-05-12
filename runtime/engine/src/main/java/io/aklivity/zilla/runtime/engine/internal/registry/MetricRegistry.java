/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.Objects.requireNonNull;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;

public class MetricRegistry
{
    private final MetricContext context;

    MetricRegistry(
        MetricContext context)
    {
        this.context = requireNonNull(context);
    }

    public MessageConsumer supplyHandler(
        LongConsumer recorder)
    {
        return context.supply(recorder);
    }

    public String group()
    {
        return context.group();
    }

    public Metric.Kind kind()
    {
        return context.kind();
    }

    public MetricContext.Direction direction()
    {
        return context.direction();
    }
}
