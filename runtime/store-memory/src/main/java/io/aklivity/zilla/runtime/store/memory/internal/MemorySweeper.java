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
package io.aklivity.zilla.runtime.store.memory.internal;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;

final class MemorySweeper
{
    private static final long CONTINUE_DELAY_MILLIS = 1L;

    private final ConcurrentMap<String, MemoryEntry> entries;
    private final ConcurrentMap<String, LockEntry> locks;
    private final MemoryExpirers expirers;
    private final AtomicLong nextSweepAt;
    private final Clock clock;
    private final long intervalMillis;
    private final int chunk;

    private Iterator<Entry<String, MemoryEntry>> pass;

    MemorySweeper(
        ConcurrentMap<String, MemoryEntry> entries,
        ConcurrentMap<String, LockEntry> locks,
        MemoryExpirers expirers,
        AtomicLong nextSweepAt,
        Clock clock,
        long intervalMillis,
        int chunk)
    {
        this.entries = entries;
        this.locks = locks;
        this.expirers = expirers;
        this.nextSweepAt = nextSweepAt;
        this.clock = clock;
        this.intervalMillis = intervalMillis;
        this.chunk = chunk;
    }

    long sweep()
    {
        final long now = clock.millis();

        if (pass == null)
        {
            claim(now);
        }

        long delayMillis = intervalMillis;

        if (pass != null)
        {
            for (int count = 0; count < chunk && pass.hasNext(); count++)
            {
                expire(pass.next(), now);
            }

            if (pass.hasNext())
            {
                delayMillis = CONTINUE_DELAY_MILLIS;
            }
            else
            {
                pass = null;
                nextSweepAt.set(now + intervalMillis);
            }
        }

        return delayMillis;
    }

    private void claim(
        long now)
    {
        final long due = nextSweepAt.get();

        if (now >= due && nextSweepAt.compareAndSet(due, Long.MAX_VALUE))
        {
            locks.values().removeIf(lock -> lock.expiresAt() <= now);
            pass = entries.entrySet().iterator();
        }
    }

    private void expire(
        Entry<String, MemoryEntry> candidate,
        long now)
    {
        final String key = candidate.getKey();
        final MemoryEntry entry = candidate.getValue();

        if (entry.expired(now) && entries.remove(key, entry) && entry.tracked())
        {
            expirers.report(key, entry.value());
        }
    }
}
