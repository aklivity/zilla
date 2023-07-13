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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.localId;
import static io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId.namespaceId;

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class ScalarRecord implements MetricRecord
{
    private final long namespacedBindingId;
    private final int namespaceId;
    private final int bindingId;
    private final int metricId;
    private final LongSupplier reader;
    private final IntFunction<String> labelResolver;

    public ScalarRecord(
        long namespacedBindingId,
        long namespacedMetricId,
        LongSupplier reader,
        IntFunction<String> labelResolver)
    {
        this.namespacedBindingId = namespacedBindingId;
        this.namespaceId = namespaceId(namespacedBindingId);
        this.bindingId = localId(namespacedBindingId);
        this.metricId = localId(namespacedMetricId);
        this.reader = reader;
        this.labelResolver = labelResolver;
    }

    @Override
    public long bindingId()
    {
        return namespacedBindingId;
    }

    @Override
    public String namespace()
    {
        return labelResolver.apply(namespaceId);
    }

    @Override
    public String binding()
    {
        return labelResolver.apply(bindingId);
    }

    @Override
    public String metric()
    {
        return labelResolver.apply(metricId);
    }

    public LongSupplier valueReader()
    {
        return this.reader;
    }

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
        ScalarRecord that = (ScalarRecord) o;
        return namespaceId == that.namespaceId && bindingId == that.bindingId && metricId == that.metricId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(namespaceId, bindingId, metricId);
    }
}
