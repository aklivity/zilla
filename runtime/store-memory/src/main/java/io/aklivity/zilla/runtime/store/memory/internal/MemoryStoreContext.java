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
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.Watcher;

final class MemoryStoreContext implements StoreContext
{
    private final LongFunction<ConcurrentMap<String, MemoryEntry>> supplyEntries;
    private final LongFunction<ConcurrentMap<String, List<Watcher>>> supplyWatchers;
    private final LongFunction<ConcurrentMap<String, LockEntry>> supplyLocks;
    private final LongConsumer removeEntries;
    private final Consumer<Runnable> dispatcher;

    MemoryStoreContext(
        LongFunction<ConcurrentMap<String, MemoryEntry>> supplyEntries,
        LongFunction<ConcurrentMap<String, List<Watcher>>> supplyWatchers,
        LongFunction<ConcurrentMap<String, LockEntry>> supplyLocks,
        LongConsumer removeEntries,
        Consumer<Runnable> dispatcher)
    {
        this.supplyEntries = supplyEntries;
        this.supplyWatchers = supplyWatchers;
        this.supplyLocks = supplyLocks;
        this.removeEntries = removeEntries;
        this.dispatcher = dispatcher;
    }

    @Override
    public StoreHandler attach(
        StoreConfig config)
    {
        return new MemoryStoreHandler(
            supplyEntries.apply(config.id),
            supplyWatchers.apply(config.id),
            supplyLocks.apply(config.id),
            dispatcher);
    }

    @Override
    public void detach(
        StoreConfig config)
    {
        removeEntries.accept(config.id);
    }
}
