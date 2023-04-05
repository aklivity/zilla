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
package io.aklivity.zilla.runtime.engine.metrics.layout;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

public abstract class MetricsLayoutRO extends LayoutRO
{
    protected static final int FIELD_SIZE = BitUtil.SIZE_OF_LONG;
    protected static final int VALUE_OFFSET = 2 * FIELD_SIZE;

    protected final AtomicBuffer buffer;

    protected MetricsLayoutRO(
        AtomicBuffer buffer)
    {
        this.buffer = buffer;
    }

    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        throw new RuntimeException("not implemented");
    }

    public abstract long[][] getIds();

    public abstract LongSupplier supplyReader(
        long bindingId,
        long metricId);

    public abstract LongSupplier[] supplyReaders(
        long bindingId,
        long metricId);

    protected abstract int recordSize();
}
