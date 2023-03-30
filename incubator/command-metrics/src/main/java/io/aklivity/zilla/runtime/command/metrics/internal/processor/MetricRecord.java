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
package io.aklivity.zilla.runtime.command.metrics.internal.processor;

import java.util.LinkedList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;

public class MetricRecord
{
    private long namespacedBindingId;
    private long namespacedMetricId;
    private int namespaceId;
    private int bindingId;
    private int metricId;
    private FileReader.Kind kind;
    private List<LongSupplier> readers;
    private IntFunction<String> labelResolver;

    public MetricRecord(
        long namespacedBindingId,
        long namespacedMetricId,
        FileReader.Kind kind,
        IntFunction<String> labelResolver)
    {
        this.namespacedBindingId = namespacedBindingId;
        this.namespacedMetricId = namespacedMetricId;
        this.namespaceId = namespaceId(namespacedBindingId);
        this.bindingId = localId(namespacedBindingId);
        this.metricId = localId(namespacedMetricId);
        this.kind = kind;
        this.readers = new LinkedList<>();
        this.labelResolver = labelResolver;
    }

    public void addReader(
        LongSupplier reader)
    {
        readers.add(reader);
    }

    public String namespaceName()
    {
        return labelResolver.apply(namespaceId);
    }

    public String bindingName()
    {
        return labelResolver.apply(bindingId);
    }

    public String metricName()
    {
        return labelResolver.apply(metricId);
    }

    public long value()
    {
        Long result = readers.stream().map(LongSupplier::getAsLong).reduce(Long::sum).orElse(0L);
        return result;
    }

    public LongSupplier valueSupplier()
    {
        return this::value;
    }

    public String stringValue()
    {
        return String.valueOf(value());
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
