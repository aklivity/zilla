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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.record;

import static io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.NamespacedId.localId;
import static io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.NamespacedId.namespaceId;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.exporter.prometheus.internal.utils.ObjectLongFunction;

public class CounterGaugeRecord implements MetricRecord
{
    private static final int UNINITIALIZED = -1;

    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier[] readers;
    private final IntFunction<String> labelResolver;
    private final ObjectLongFunction<String, String[]> valueFormatter;

    private long value = UNINITIALIZED;

    public CounterGaugeRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[] readers,
        IntFunction<String> labelResolver,
        ObjectLongFunction<String, String[]> valueFormatter)
    {
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.readers = readers;
        this.labelResolver = labelResolver;
        this.valueFormatter = valueFormatter;
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
        return valueFormatter.apply(new String[]{metricName(), namespaceName(), bindingName()}, value);
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
