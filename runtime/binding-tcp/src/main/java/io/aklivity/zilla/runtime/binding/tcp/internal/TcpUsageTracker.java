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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY_LIMIT;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.EngineContext;

public final class TcpUsageTracker
{
    private final int capacity;
    private final LongConsumer recordUsage;

    private int usage;

    public TcpUsageTracker(
        TcpConfiguration config,
        EngineContext context)
    {
        this.capacity = ENGINE_WORKER_CAPACITY_LIMIT.getAsInt(config);
        this.recordUsage = context.supplyUtilizationMetric();
    }

    public int available()
    {
        return capacity - usage;
    }

    public void claim()
    {
        int newUsage = ++usage;
        assert newUsage <= capacity : "newUsage = %d, capacity = %d".formatted(newUsage, capacity);

        record(newUsage);
    }

    public void released()
    {
        int newUsage = --usage;
        assert newUsage >= 0 : "newUsage = %d".formatted(newUsage);

        record(newUsage);
    }

    private void record(
        int newUsage)
    {
        final int newUsageAsPercentage = newUsage * 100 / capacity;

        recordUsage.accept(newUsageAsPercentage);
    }
}
