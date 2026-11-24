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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.Store;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.Watcher;

final class MemoryStore implements Store
{
    static final String NAME = "memory";

    private static final int SWEEP_CHUNK = 1024;

    private final ConcurrentMap<Long, MemoryStorage> storage;
    private final long sweepIntervalMillis;

    MemoryStore(
        MemoryStoreConfiguration config)
    {
        this.sweepIntervalMillis = config.sweepInterval().toMillis();
        this.storage = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public StoreContext supply(
        EngineContext context)
    {
        return new MemoryStoreContext(
            this::acquireEntries,
            this::supplyWatchers,
            this::supplyLocks,
            this::supplyExpirers,
            this::supplyNextSweepAt,
            this::releaseEntries,
            context::dispatch,
            context.signaler(),
            context.clock(),
            sweepIntervalMillis,
            SWEEP_CHUNK);
    }

    private ConcurrentMap<String, MemoryEntry> acquireEntries(
        long storeId)
    {
        final MemoryStorage memoryStorage = storage.computeIfAbsent(storeId, id -> new MemoryStorage());
        memoryStorage.refs.incrementAndGet();
        return memoryStorage.entries;
    }

    private ConcurrentMap<String, List<Watcher>> supplyWatchers(
        long storeId)
    {
        // attach already incremented refs via acquireEntries
        return storage.computeIfAbsent(storeId, id -> new MemoryStorage()).watchers;
    }

    private ConcurrentMap<String, LockEntry> supplyLocks(
        long storeId)
    {
        // attach already incremented refs via acquireEntries
        return storage.computeIfAbsent(storeId, id -> new MemoryStorage()).locks;
    }

    private MemoryExpirers supplyExpirers(
        long storeId)
    {
        // attach already incremented refs via acquireEntries
        return storage.computeIfAbsent(storeId, id -> new MemoryStorage()).expirers;
    }

    private AtomicLong supplyNextSweepAt(
        long storeId)
    {
        // attach already incremented refs via acquireEntries
        return storage.computeIfAbsent(storeId, id -> new MemoryStorage()).nextSweepAt;
    }

    private void releaseEntries(
        long storeId)
    {
        storage.computeIfPresent(storeId, (id, ms) -> ms.refs.decrementAndGet() == 0 ? null : ms);
    }

    private static final class MemoryStorage
    {
        final AtomicInteger refs = new AtomicInteger();
        final ConcurrentMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();
        final ConcurrentMap<String, List<Watcher>> watchers = new ConcurrentHashMap<>();
        final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();
        final MemoryExpirers expirers = new MemoryExpirers();
        final AtomicLong nextSweepAt = new AtomicLong();
    }
}
