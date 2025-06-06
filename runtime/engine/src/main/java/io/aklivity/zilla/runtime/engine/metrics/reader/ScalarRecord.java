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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.namespace.NamespacedId.namespaceId;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public class ScalarRecord implements MetricRecord
{
    private final long bindingId;
    private final long metricId;
    private final int namespaceId;
    private final LongSupplier reader;
    private final LongFunction<String> labelResolver;

    public ScalarRecord(
        long bindingId,
        long metricId,
        LongSupplier reader,
        LongFunction<String> labelResolver)
    {
        this.bindingId = bindingId;
        this.metricId = metricId;
        this.namespaceId = namespaceId(bindingId);
        this.reader = reader;
        this.labelResolver = labelResolver;
    }

    @Override
    public long bindingId()
    {
        return bindingId;
    }

    @Override
    public String namespace()
    {
        // implicit int -> long conversion, it's OK
        return namespaceId != NamespacedId.NO_NAMESPACE_ID ? labelResolver.apply(namespaceId) : null;
    }

    @Override
    public String binding()
    {
        return bindingId != NamespacedId.NO_LOCAL_ID ? labelResolver.apply(bindingId) : null;
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

    public DoubleSupplier millisecondsValueReader()
    {
        return () -> (double) this.valueReader().getAsLong() / 1_000_000L;
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
