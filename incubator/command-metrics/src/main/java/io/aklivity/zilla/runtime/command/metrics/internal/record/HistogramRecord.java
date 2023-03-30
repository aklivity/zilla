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

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.HISTOGRAM;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;

public class HistogramRecord implements MetricRecord
{
    private long packedBindingId;
    private long packedMetricId;
    private int namespaceId;
    private int bindingId;
    private int metricId;
    private FileReader.Kind kind;
    private List<LongSupplier[]> readers;
    private IntFunction<String> labelResolver;

    public HistogramRecord(
        long packedBindingId,
        long packedMetricId,
        FileReader.Kind kind,
        List<LongSupplier[]> readers,
        IntFunction<String> labelResolver)
    {
        this.packedBindingId = packedBindingId;
        this.packedMetricId = packedMetricId;
        this.namespaceId = namespaceId(packedBindingId);
        this.bindingId = localId(packedBindingId);
        this.metricId = localId(packedMetricId);
        this.kind = kind;
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
    public FileReader.Kind kind()
    {
        return HISTOGRAM;
    }

    @Override
    public String stringValue()
    {
        // TODO: Ati
        return Arrays.toString(new long[]{value()});
        //return String.valueOf(value());
    }

    private long value()
    {
        // TODO: Ati
        Long result = readers.stream().map(i -> i[0].getAsLong()).reduce(Long::sum).orElse(0L);
        return result;
    }

    private static int namespaceId(
        long packedId)
    {
        return (int) (packedId >> Integer.SIZE) & 0xffff_ffff;
    }

    private static int localId(
        long packedId)
    {
        return (int) (packedId >> 0) & 0xffff_ffff;
    }
}
