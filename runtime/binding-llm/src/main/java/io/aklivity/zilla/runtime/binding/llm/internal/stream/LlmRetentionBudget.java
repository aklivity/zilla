/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.concurrent.atomic.AtomicLong;

public final class LlmRetentionBudget
{
    private final long memoryLimit;
    private final long diskLimit;
    private final AtomicLong memoryHeld = new AtomicLong();
    private final AtomicLong diskHeld = new AtomicLong();

    public LlmRetentionBudget(
        long memoryLimit,
        long diskLimit)
    {
        this.memoryLimit = memoryLimit;
        this.diskLimit = diskLimit;
    }

    boolean reserveMemory(
        long bytes)
    {
        return reserve(memoryHeld, memoryLimit, bytes);
    }

    void releaseMemory(
        long bytes)
    {
        memoryHeld.addAndGet(-bytes);
    }

    boolean reserveDisk(
        long bytes)
    {
        return reserve(diskHeld, diskLimit, bytes);
    }

    void releaseDisk(
        long bytes)
    {
        diskHeld.addAndGet(-bytes);
    }

    long memoryHeld()
    {
        return memoryHeld.get();
    }

    long diskHeld()
    {
        return diskHeld.get();
    }

    private static boolean reserve(
        AtomicLong held,
        long limit,
        long bytes)
    {
        long current;
        long next;
        boolean reserved;

        do
        {
            current = held.get();
            next = current + bytes;
            reserved = limit < 0 || next <= limit;
        }
        while (reserved && !held.compareAndSet(current, next));

        return reserved;
    }
}
