/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.command.metrics.internal.record;

import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.localId;
import static io.aklivity.zilla.runtime.command.metrics.internal.utils.MetricUtils.namespaceId;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class CounterRecord implements MetricRecord
{
    private static final int UNINITIALIZED = -1;

    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier[] readers;
    private final IntFunction<String> labelResolver;

    private long value = UNINITIALIZED;

    public CounterRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[] readers,
        IntFunction<String> labelResolver)
    {
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.readers = readers;
        this.labelResolver = labelResolver;
    }

    @Override
    public String namespaceName()
    {
        return labelResolver.apply(namespaceId);
    }

    @Override
    public String bindingName()
    {
        return labelResolver.apply(bindingId);
    }

    @Override
    public String metricName()
    {
        return labelResolver.apply(metricId);
    }

    @Override
    public String stringValue()
    {
        if (value == UNINITIALIZED)
        {
            update();
        }
        return String.valueOf(value);
    }

    @Override
    public void update()
    {
        value = value();
    }

    private long value()
    {
        return Arrays.stream(readers).map(LongSupplier::getAsLong).reduce(Long::sum).orElse(0L);
    }

}
