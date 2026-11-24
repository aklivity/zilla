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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.Watcher;

final class MemoryStoreContext implements StoreContext
{
    private final LongFunction<ConcurrentMap<String, MemoryEntry>> supplyEntries;
    private final LongFunction<ConcurrentMap<String, List<Watcher>>> supplyWatchers;
    private final LongFunction<ConcurrentMap<String, LockEntry>> supplyLocks;
    private final LongFunction<MemoryExpirers> supplyExpirers;
    private final LongFunction<AtomicLong> supplyNextSweepAt;
    private final Map<Long, MemoryStoreHandler> handlers = new HashMap<>();
    private final Map<Long, SweepTimer> timers = new HashMap<>();
    private final LongConsumer removeEntries;
    private final Consumer<Runnable> dispatcher;
    private final Signaler signaler;
    private final Clock clock;
    private final long sweepIntervalMillis;
    private final int sweepChunk;

    MemoryStoreContext(
        LongFunction<ConcurrentMap<String, MemoryEntry>> supplyEntries,
        LongFunction<ConcurrentMap<String, List<Watcher>>> supplyWatchers,
        LongFunction<ConcurrentMap<String, LockEntry>> supplyLocks,
        LongFunction<MemoryExpirers> supplyExpirers,
        LongFunction<AtomicLong> supplyNextSweepAt,
        LongConsumer removeEntries,
        Consumer<Runnable> dispatcher,
        Signaler signaler,
        Clock clock,
        long sweepIntervalMillis,
        int sweepChunk)
    {
        this.supplyEntries = supplyEntries;
        this.supplyWatchers = supplyWatchers;
        this.supplyLocks = supplyLocks;
        this.supplyExpirers = supplyExpirers;
        this.supplyNextSweepAt = supplyNextSweepAt;
        this.removeEntries = removeEntries;
        this.dispatcher = dispatcher;
        this.signaler = signaler;
        this.clock = clock;
        this.sweepIntervalMillis = sweepIntervalMillis;
        this.sweepChunk = sweepChunk;
    }

    @Override
    public StoreHandler attach(
        StoreConfig config)
    {
        final ConcurrentMap<String, MemoryEntry> entries = supplyEntries.apply(config.id);
        final ConcurrentMap<String, LockEntry> locks = supplyLocks.apply(config.id);
        final MemoryExpirers expirers = supplyExpirers.apply(config.id);

        final MemoryStoreHandler handler = new MemoryStoreHandler(
            entries,
            supplyWatchers.apply(config.id),
            locks,
            expirers,
            dispatcher,
            clock);
        handlers.put(config.id, handler);

        final SweepTimer timer = new SweepTimer(new MemorySweeper(
            entries,
            locks,
            expirers,
            supplyNextSweepAt.apply(config.id),
            clock,
            sweepIntervalMillis,
            sweepChunk));
        timers.put(config.id, timer);
        timer.schedule(sweepIntervalMillis);

        return handler;
    }

    @Override
    public void detach(
        StoreConfig config)
    {
        final SweepTimer timer = timers.remove(config.id);
        if (timer != null)
        {
            timer.cancel();
        }

        final MemoryStoreHandler handler = handlers.remove(config.id);
        if (handler != null)
        {
            handler.unwatchAll();
            handler.unexpireAll();
        }
        removeEntries.accept(config.id);
    }

    private final class SweepTimer implements IntConsumer
    {
        private final MemorySweeper sweeper;

        private long cancelId = Signaler.NO_CANCEL_ID;

        private SweepTimer(
            MemorySweeper sweeper)
        {
            this.sweeper = sweeper;
        }

        @Override
        public void accept(
            int signalId)
        {
            schedule(sweeper.sweep());
        }

        private void schedule(
            long delayMillis)
        {
            cancelId = signaler.signalAt(clock.millis() + delayMillis, 0, this);
        }

        private void cancel()
        {
            signaler.cancel(cancelId);
        }
    }
}
