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

import java.util.List;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;

public class MetricRecord
{
    public long namespacedBindingId;
    public long namespacedMetricId;
    public FileReader.Kind kind;
    public List<LongSupplier> readers;

    public MetricRecord(
        long namespacedBindingId,
        long namespacedMetricId,
        FileReader.Kind kind)
    {
        this.namespacedBindingId = namespacedBindingId;
        this.namespacedMetricId = namespacedMetricId;
        this.kind = kind;
    }

    public void addReader(
        LongSupplier reader)
    {
        readers.add(reader);
    }

}
