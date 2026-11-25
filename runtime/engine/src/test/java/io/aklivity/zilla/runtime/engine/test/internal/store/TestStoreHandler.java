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

import java.io.Closeable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class TestStoreHandler implements StoreHandler
{
    private static final String INCREMENT = "increment";
    private static final String REPLACE = "replace";

    private final ConcurrentMap<String, TestEntry> entries;
    private final ConcurrentMap<String, List<TestWatcher>> listeners;
    private final ConcurrentMap<String, TestLockEntry> locks;
    private final List<TestExpirer> expirers;
    private final Set<Expiry> expiries = ConcurrentHashMap.newKeySet();
    private final Consumer<Runnable> dispatcher;
    private final LongSupplier clock;
    private final BiConsumer<Long, Runnable> scheduler;
    private final Queue<Assertion> assertions;

    public TestStoreHandler(
        StoreConfig store,
        Consumer<Runnable> dispatcher,
        ConcurrentMap<String, TestEntry> entries,
        ConcurrentMap<String, List<TestWatcher>> listeners,
        ConcurrentMap<String, TestLockEntry> locks,
        Queue<Assertion> assertions)
    {
        this(store, dispatcher, System::currentTimeMillis, (time, task) -> {}, entries, listeners, new CopyOnWriteArrayList<>(),
            locks, assertions);
    }

    public TestStoreHandler(
        StoreConfig store,
        Consumer<Runnable> dispatcher,
        LongSupplier clock,
        BiConsumer<Long, Runnable> scheduler,
        ConcurrentMap<String, TestEntry> entries,
        ConcurrentMap<String, List<TestWatcher>> listeners,
        List<TestExpirer> expirers,
        ConcurrentMap<String, TestLockEntry> locks,
        Queue<Assertion> assertions)
    {
        this.entries = Objects.requireNonNull(entries);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.listeners = Objects.requireNonNull(listeners);
        this.expirers = Objects.requireNonNull(expirers);
        this.locks = Objects.requireNonNull(locks);
        this.assertions = assertions;
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final String value = live(entries.get(key));
        defer(() -> completion.accept(key, value));
    }

    @Override
    public void put(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        assertOperation(new Assertion("put", key, value, ttl));
        final TestEntry next = new TestEntry(value, expiresAt(ttl));
        final TestEntry previous = entries.put(key, next);
        expireReplaced(key, previous);
        scheduleExpiry(key, next);
        notifyListeners(key, value);
        defer(() -> completion.accept(null));
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        assertOperation(new Assertion("putIfAbsent", key, value, ttl));
        final TestEntry next = new TestEntry(value, expiresAt(ttl));
        final TestEntry existing = entries.putIfAbsent(key, next);
        boolean stored = existing == null;
        if (existing != null && existing.expired(clock.getAsLong()))
        {
            stored = entries.replace(key, existing, next);
            if (stored)
            {
                expireReplaced(key, existing);
            }
        }
        if (stored)
        {
            scheduleExpiry(key, next);
            notifyListeners(key, value);
        }
        final String result = live(existing);
        defer(() -> completion.accept(result));
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        assertOperation(new Assertion("delete", key, null, null));
        final TestEntry removed = entries.remove(key);
        if (removed != null)
        {
            expireReplaced(key, removed);
            notifyListeners(key, null);
        }
        defer(() -> completion.accept(null));
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        assertOperation(new Assertion("getAndDelete", key, null, null));
        final TestEntry prior = entries.remove(key);
        if (prior != null)
        {
            expireReplaced(key, prior);
            notifyListeners(key, null);
        }
        final String result = live(prior);
        defer(() -> completion.accept(result));
    }

    @Override
    public void increment(
        String key,
        long delta,
        Duration ttl,
        BiConsumer<String, Long> completion)
    {
        long counter;
        boolean updated;
        boolean alive;
        TestEntry existing;
        TestEntry next;
        do
        {
            existing = entries.get(key);
            alive = existing != null && !existing.expired(clock.getAsLong());
            counter = (alive ? Long.parseLong(existing.value()) : 0L) + delta;
            next = new TestEntry(Long.toString(counter), alive ? existing.expiresAt() : expiresAt(ttl));
            updated = existing == null ? entries.putIfAbsent(key, next) == null : entries.replace(key, existing, next);
        }
        while (!updated);
        if (!alive)
        {
            expireReplaced(key, existing);
            scheduleExpiry(key, next);
        }
        assertOperation(new Assertion("increment", key, null, Long.toString(counter), null, delta, ttl));
        notifyListeners(key, Long.toString(counter));
        final long result = counter;
        defer(() -> completion.accept(key, result));
    }

    @Override
    public void get(
        List<String> keys,
        Consumer<List<String>> completion)
    {
        final String[] values = new String[keys.size()];
        for (int index = 0; index < values.length; index++)
        {
            values[index] = live(entries.get(keys.get(index)));
        }
        defer(() -> completion.accept(Arrays.asList(values)));
    }

    @Override
    public void delete(
        List<String> keys,
        LongConsumer completion)
    {
        assertOperation(new Assertion("delete", null, keys, null, null, 0L, null));
        long removed = 0L;
        for (String key : keys)
        {
            final TestEntry entry = entries.remove(key);
            if (entry != null)
            {
                expireReplaced(key, entry);
                notifyListeners(key, null);
                if (!entry.expired(clock.getAsLong()))
                {
                    removed++;
                }
            }
        }
        final long count = removed;
        defer(() -> completion.accept(count));
    }

    @Override
    public void replace(
        String key,
        String expected,
        String value,
        Duration ttl,
        Consumer<Boolean> completion)
    {
        assertOperation(new Assertion("replace", key, null, value, expected, 0L, ttl));
        boolean replaced;
        boolean contended;
        do
        {
            final TestEntry existing = entries.get(key);
            final boolean matches = existing != null && !existing.expired(clock.getAsLong()) && existing.value().equals(expected);
            replaced = matches && entries.replace(key, existing, new TestEntry(value, expiresAt(ttl)));
            contended = matches && !replaced;
        }
        while (contended);
        if (replaced)
        {
            notifyListeners(key, value);
        }
        final boolean result = replaced;
        defer(() -> completion.accept(result));
    }

    @Override
    public void touch(
        String key,
        Duration ttl,
        Consumer<Boolean> completion)
    {
        assertOperation(new Assertion("touch", key, null, ttl));
        TestEntry existing;
        TestEntry next;
        boolean live;
        boolean touched;
        boolean contended;
        do
        {
            existing = entries.get(key);
            live = existing != null && !existing.expired(clock.getAsLong());
            next = live ? new TestEntry(existing.value(), expiresAt(ttl)) : null;
            touched = live && entries.replace(key, existing, next);
            contended = live && !touched;
        }
        while (contended);
        if (touched)
        {
            scheduleExpiry(key, next);
        }
        else if (existing != null)
        {
            expireIfCovered(key, existing);
        }
        final boolean result = touched;
        defer(() -> completion.accept(result));
    }

    @Override
    public void lock(
        String key,
        Duration ttl,
        BiConsumer<String, String> completion)
    {
        final long now = System.currentTimeMillis();
        final long expiresAt = ttl == null ? Long.MAX_VALUE : now + ttl.toMillis();
        final String token = UUID.randomUUID().toString();
        final TestLockEntry candidate = new TestLockEntry(token, expiresAt);
        TestLockEntry existing = locks.putIfAbsent(key, candidate);
        if (existing != null && existing.expiresAt() <= now)
        {
            existing = locks.replace(key, existing, candidate) ? null : locks.get(key);
        }
        final String result = existing == null ? token : null;
        defer(() -> completion.accept(key, result));
    }

    @Override
    public void unlock(
        String key,
        String token,
        Consumer<String> completion)
    {
        final long now = System.currentTimeMillis();
        final TestLockEntry current = locks.get(key);
        final String result;
        if (current != null && current.expiresAt() > now && current.token().equals(token))
        {
            locks.remove(key, current);
            result = token;
        }
        else
        {
            if (current != null && current.expiresAt() <= now)
            {
                locks.remove(key, current);
            }
            result = null;
        }
        defer(() -> completion.accept(result));
    }

    @Override
    public void renew(
        String key,
        String token,
        Duration ttl,
        Consumer<String> completion)
    {
        final long now = System.currentTimeMillis();
        final TestLockEntry current = locks.get(key);
        String result = null;
        if (current != null && current.expiresAt() > now && current.token().equals(token))
        {
            final long expiresAt = ttl == null ? Long.MAX_VALUE : now + ttl.toMillis();
            final TestLockEntry renewed = new TestLockEntry(token, expiresAt);
            if (locks.replace(key, current, renewed))
            {
                result = token;
            }
        }
        else if (current != null && current.expiresAt() <= now)
        {
            locks.remove(key, current);
        }
        final String outcome = result;
        defer(() -> completion.accept(outcome));
    }

    @Override
    public Closeable watch(
        String key,
        BiConsumer<String, String> listener)
    {
        final TestWatcher watcher = new TestWatcher(listener, dispatcher);
        final List<TestWatcher> list = listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(watcher);
        return () ->
        {
            final List<TestWatcher> current = listeners.get(key);
            if (current != null)
            {
                current.remove(watcher);
            }
        };
    }

    @Override
    public Closeable expire(
        String prefix,
        BiConsumer<String, String> listener)
    {
        final TestExpirer expirer = new TestExpirer(Objects.requireNonNull(prefix), listener, dispatcher);
        final Expiry expiry = new Expiry(expirer);
        expirers.add(expirer);
        expiries.add(expiry);
        entries.forEach((key, entry) ->
        {
            if (key.startsWith(prefix) && entry.expired(clock.getAsLong()))
            {
                expire(key, entry);
            }
        });
        return expiry;
    }

    private void scheduleExpiry(
        String key,
        TestEntry entry)
    {
        if (entry.expiresAt() != Long.MAX_VALUE)
        {
            scheduler.accept(entry.expiresAt() + 1L, () -> expireIfCovered(key, entry));
        }
    }

    private void expireIfCovered(
        String key,
        TestEntry entry)
    {
        if (expirers.stream().anyMatch(expirer -> key.startsWith(expirer.prefix())))
        {
            expire(key, entry);
        }
    }

    private void expireReplaced(
        String key,
        TestEntry replaced)
    {
        if (replaced != null && replaced.expired(clock.getAsLong()))
        {
            notifyExpirers(key, replaced.value());
        }
    }

    private void expire(
        String key,
        TestEntry entry)
    {
        if (entries.remove(key, entry))
        {
            notifyExpirers(key, entry.value());
        }
    }

    private void notifyExpirers(
        String key,
        String value)
    {
        for (TestExpirer expirer : expirers)
        {
            if (key.startsWith(expirer.prefix()))
            {
                expirer.dispatcher().accept(() -> notifyExpirer(expirer, key, value));
            }
        }
    }

    private void notifyExpirer(
        TestExpirer expirer,
        String key,
        String value)
    {
        if (expirers.contains(expirer))
        {
            expirer.listener().accept(key, value);
        }
    }

    void unexpireAll()
    {
        expiries.forEach(Expiry::close);
    }

    private void assertOperation(
        Assertion actual)
    {
        if (assertions != null)
        {
            final Assertion expected = assertions.poll();

            if (expected == null)
            {
                throw new AssertionError(String.format("unexpected store operation %s %s", actual.op, target(actual)));
            }

            if (!matches(expected, actual))
            {
                throw new AssertionError(String.format(
                    "expected store operation %s, was %s", describe(expected), describe(actual)));
            }
        }
    }

    private static boolean matches(
        Assertion expected,
        Assertion actual)
    {
        return expected.op.equals(actual.op) &&
            Objects.equals(expected.key, actual.key) &&
            Objects.equals(expected.keys, actual.keys) &&
            (INCREMENT.equals(expected.op) && expected.value == null || Objects.equals(expected.value, actual.value)) &&
            Objects.equals(expected.expected, actual.expected) &&
            expected.delta == actual.delta &&
            Objects.equals(expected.ttl, actual.ttl);
    }

    private static String describe(
        Assertion assertion)
    {
        final String target = target(assertion);

        return switch (assertion.op)
        {
        case INCREMENT -> String.format("%s %s delta=%d value=%s ttl=%s",
            assertion.op, target, assertion.delta, assertion.value, assertion.ttl);
        case REPLACE -> String.format("%s %s expected=%s value=%s ttl=%s",
            assertion.op, target, assertion.expected, assertion.value, assertion.ttl);
        default -> String.format("%s %s value=%s ttl=%s",
            assertion.op, target, assertion.value, assertion.ttl);
        };
    }

    private static String target(
        Assertion assertion)
    {
        return assertion.keys != null ? assertion.keys.toString() : assertion.key;
    }

    private void notifyListeners(
        String key,
        String value)
    {
        final List<TestWatcher> list = listeners.get(key);
        if (list != null && !list.isEmpty())
        {
            for (TestWatcher w : list)
            {
                w.dispatcher().accept(() -> w.listener().accept(key, value));
            }
        }
    }

    private String live(
        TestEntry entry)
    {
        return entry != null && !entry.expired(clock.getAsLong()) ? entry.value() : null;
    }

    private long expiresAt(
        Duration ttl)
    {
        return ttl == null ? Long.MAX_VALUE : clock.getAsLong() + ttl.toMillis();
    }

    private void defer(
        Runnable task)
    {
        dispatcher.accept(task);
    }

    private final class Expiry implements Closeable
    {
        private final TestExpirer expirer;

        private Expiry(
            TestExpirer expirer)
        {
            this.expirer = expirer;
        }

        @Override
        public void close()
        {
            expirers.remove(expirer);
            expiries.remove(this);
        }
    }
}
