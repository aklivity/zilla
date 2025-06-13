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
    private final int capacity;
    private final MutableInteger usage;
    private final LongConsumer recordUsage;

    public TcpCapacityTracker(
        TcpConfiguration config,
        EngineContext context)
    {
        this.capacity = ENGINE_WORKER_CAPACITY.getAsInt(config);
        this.usage = new MutableInteger(0);
        this.recordUsage = context.supplyUtilizationMetric();
    }

    public int available()
    {
        return capacity - usage.get();
    }

    public void onConnected()
    {
        int newUsage = usage.incrementAndGet();
        assert newUsage <= capacity;

        onUsageChanged(newUsage);
    }

    public void onClosed()
    {
        int newUsage = usage.decrementAndGet();
        assert newUsage >= 0;

        onUsageChanged(newUsage);
    }

    private void onUsageChanged(
        int newUsage)
    {
        final int newUsageAsPercentage = newUsage * 100 / capacity;

        recordUsage.accept(newUsageAsPercentage);
    }
}
