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

    private final LongSupplier[] readers;
    private final ObjectLongFunction<String, String[]> valueFormatter;
    private final String namespaceName;
    private final String bindingName;
    private final String metricName;

    private long value = UNINITIALIZED;

    public CounterGaugeRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier[] readers,
        IntFunction<String> labelResolver,
        ObjectLongFunction<String, String[]> valueFormatter)
    {
        this.readers = readers;
        this.valueFormatter = valueFormatter;
        this.namespaceName = labelResolver.apply(namespaceId(packedBindingId));
        this.bindingName = labelResolver.apply(localId(packedBindingId));
        this.metricName = labelResolver.apply(localId(packedMetricId));
    }

    @Override
    public String namespaceName()
    {
        return namespaceName;
    }

    @Override
    public String bindingName()
    {
        return bindingName;
    }

    @Override
    public String metricName()
    {
        return metricName;
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
