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

import org.agrona.collections.MutableInteger;

public final class CapacityTracker
{
    private final MutableInteger capacity;
    private final TcpEventContext eventContext;
    private final int initialCapacity;

    private int capacityPercentage;

    public CapacityTracker(
        int initialCapacity,
        TcpEventContext eventContext)
    {
        this.eventContext = eventContext;
        this.initialCapacity = initialCapacity;
        this.capacity = new MutableInteger(initialCapacity);
    }

    public int capacity()
    {
        return capacity.get();
    }

    public int incrementAndGet(
        long bindingId)
    {
        int newCapacity = capacity.incrementAndGet();
        capacityChanged(bindingId, newCapacity);

        return newCapacity;
    }

    public int decrementAndGet(
        long bindingId)
    {
        int newCapacity = capacity.decrementAndGet();
        capacityChanged(bindingId, newCapacity);

        return newCapacity;
    }

    public int get()
    {
        return capacity.get();
    }

    private void capacityChanged(
        long bindingId,
        int newCapacity)
    {
        int newCapacityPercentage = 100 - (newCapacity * 100 / initialCapacity);

        if (Math.abs(capacityPercentage - newCapacityPercentage) >= 1)
        {
            eventContext.usageChanged(bindingId, newCapacityPercentage);
        }

        capacityPercentage = newCapacityPercentage;
    }
}
