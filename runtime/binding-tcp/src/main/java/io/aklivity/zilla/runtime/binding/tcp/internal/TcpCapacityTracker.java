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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY;

import java.util.function.LongConsumer;

import org.agrona.collections.MutableInteger;

import io.aklivity.zilla.runtime.engine.EngineContext;

public final class TcpCapacityTracker
{
    private final MutableInteger capacity;
    private final LongConsumer capacityUsage;
    private final int initialCapacity;

    private int capacityPercentage;

    public TcpCapacityTracker(
        TcpConfiguration config,
        EngineContext context)
    {
        this.initialCapacity = ENGINE_WORKER_CAPACITY.getAsInt(config);
        this.capacity = new MutableInteger(initialCapacity);
        this.capacityUsage = context.supplyUtilizationMetric();
    }

    public int capacity()
    {
        return capacity.get();
    }

    public int incrementAndGet()
    {
        int newCapacity = capacity.incrementAndGet();
        capacityChanged(newCapacity);

        return newCapacity;
    }

    public int decrementAndGet()
    {
        int newCapacity = capacity.decrementAndGet();
        capacityChanged(newCapacity);

        return newCapacity;
    }

    public int get()
    {
        return capacity.get();
    }

    private void capacityChanged(
        int newCapacity)
    {
        final int newCapacityPercentage = 100 - (newCapacity * 100 / initialCapacity);
        final int percentageDiff = newCapacityPercentage - capacityPercentage;

        if (Math.abs(percentageDiff) >= 1)
        {
            capacityUsage.accept(percentageDiff);
        }

        capacityPercentage = newCapacityPercentage;
    }
}
