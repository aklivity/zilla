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
package io.aklivity.zilla.runtime.engine.metrics.record;

import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.localId;
import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.namespaceId;

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class CounterGaugeRecord implements MetricRecord
{
    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier reader;
    private final IntFunction<String> labelResolver;

    public CounterGaugeRecord(
        long packedBindingId,
        long packedMetricId,
        LongSupplier reader,
        IntFunction<String> labelResolver)
    {
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.reader = reader;
        this.labelResolver = labelResolver;
    }

    @Override
    public String namespaceName()
    {
        return labelResolver.apply(namespaceId);
    }

    @Override
    public int bindingId()
    {
        return bindingId;
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

    public LongSupplier valueReader()
    {
        return this.reader;
    }

    /*private long aggregateValues()
    {
        long result = 0;
        for (LongSupplier reader: readers)
        {
            result += reader.getAsLong();
        }
        return result;
    }*/

    @Override
    public boolean equals(
        Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        CounterGaugeRecord that = (CounterGaugeRecord) o;
        return namespaceId == that.namespaceId && bindingId == that.bindingId && metricId == that.metricId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(namespaceId, bindingId, metricId);
    }
}
