/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.test.internal.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.engine.test.internal.TestContextClosures;

public final class TestStoreContext implements StoreContext
{
    private final Consumer<Runnable> dispatcher;
    private final LongSupplier clock;
    private final BiConsumer<Long, Runnable> scheduler;
    private final Map<Long, TestStoreHandler> handlers = new HashMap<>();
    private final LongFunction<ConcurrentMap<String, TestEntry>> supplyEntries;
    private final LongFunction<ConcurrentMap<String, List<TestWatcher>>> supplyListeners;
    private final LongFunction<List<TestExpirer>> supplyExpirers;
    private final LongFunction<ConcurrentMap<String, TestLockEntry>> supplyLocks;
    private final BiFunction<Long, List<Assertion>, Queue<Assertion>> supplyAssertions;

    public TestStoreContext(
        EngineContext context,
        LongFunction<ConcurrentMap<String, TestEntry>> supplyEntries,
        LongFunction<ConcurrentMap<String, List<TestWatcher>>> supplyListeners,
        LongFunction<List<TestExpirer>> supplyExpirers,
        LongFunction<ConcurrentMap<String, TestLockEntry>> supplyLocks,
        BiFunction<Long, List<Assertion>, Queue<Assertion>> supplyAssertions)
    {
        this.dispatcher = context::dispatch;
        this.clock = () -> context.clock().millis();
        this.scheduler = (time, task) -> context.signaler().signalAt(time, 0, id -> task.run());
        this.supplyEntries = supplyEntries;
        this.supplyListeners = supplyListeners;
        this.supplyExpirers = supplyExpirers;
        this.supplyLocks = supplyLocks;
        this.supplyAssertions = supplyAssertions;
    }

    @Override
    public TestStoreHandler attach(
        StoreConfig store)
    {
        final ConcurrentMap<String, TestEntry> entries = supplyEntries.apply(store.id);
        final TestStoreOptionsConfig options = store.options instanceof TestStoreOptionsConfig test ? test : null;
        if (options != null && options.entries != null)
        {
            options.entries.forEach((key, value) -> entries.putIfAbsent(key, new TestEntry(value, Long.MAX_VALUE)));
        }
        final Queue<Assertion> assertions = options != null ? supplyAssertions.apply(store.id, options.assertions) : null;
        final ConcurrentMap<String, List<TestWatcher>> listeners = supplyListeners.apply(store.id);
        final ConcurrentMap<String, TestLockEntry> locks = supplyLocks.apply(store.id);
        final TestStoreHandler handler = new TestStoreHandler(store, dispatcher, clock, scheduler, entries, listeners,
            supplyExpirers.apply(store.id), locks, assertions);
        handlers.put(store.id, handler);
        return handler;
    }

    @Override
    public void detach(
        StoreConfig store)
    {
        final TestStoreHandler handler = handlers.remove(store.id);
        if (handler != null)
        {
            handler.unexpireAll();
        }

        final Queue<Assertion> assertions = supplyAssertions.apply(store.id, null);

        if (assertions != null && !assertions.isEmpty())
        {
            throw new AssertionError(String.format("missing %d expected store operations", assertions.size()));
        }
    }

    @Override
    public void close()
    {
        TestContextClosures.record("store");
    }
}
