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
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class TestStoreHandler implements StoreHandler
{
    private final ConcurrentMap<String, TestEntry> entries;
    private final ConcurrentMap<String, List<TestWatcher>> listeners;
    private final ConcurrentMap<String, TestLockEntry> locks;
    private final Consumer<Runnable> dispatcher;

    public TestStoreHandler(
        StoreConfig store,
        Consumer<Runnable> dispatcher,
        ConcurrentMap<String, TestEntry> entries,
        ConcurrentMap<String, List<TestWatcher>> listeners,
        ConcurrentMap<String, TestLockEntry> locks)
    {
        this.entries = Objects.requireNonNull(entries);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.listeners = Objects.requireNonNull(listeners);
        this.locks = Objects.requireNonNull(locks);
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
        entries.put(key, new TestEntry(value, expiresAt(ttl)));
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
        final TestEntry next = new TestEntry(value, expiresAt(ttl));
        final TestEntry existing = entries.putIfAbsent(key, next);
        boolean stored = existing == null;
        if (existing != null && existing.expired())
        {
            stored = entries.replace(key, existing, next);
        }
        if (stored)
        {
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
        final TestEntry removed = entries.remove(key);
        if (removed != null)
        {
            notifyListeners(key, null);
        }
        defer(() -> completion.accept(null));
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final TestEntry prior = entries.remove(key);
        if (prior != null)
        {
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
        do
        {
            final TestEntry existing = entries.get(key);
            final boolean alive = existing != null && !existing.expired();
            counter = (alive ? Long.parseLong(existing.value()) : 0L) + delta;
            final TestEntry next = new TestEntry(Long.toString(counter), alive ? existing.expiresAt() : expiresAt(ttl));
            updated = existing == null ? entries.putIfAbsent(key, next) == null : entries.replace(key, existing, next);
        }
        while (!updated);
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
        long removed = 0L;
        for (String key : keys)
        {
            final TestEntry entry = entries.remove(key);
            if (entry != null)
            {
                notifyListeners(key, null);
                if (!entry.expired())
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
        boolean replaced;
        boolean contended;
        do
        {
            final TestEntry existing = entries.get(key);
            final boolean matches = existing != null && !existing.expired() && existing.value().equals(expected);
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

    private static String live(
        TestEntry entry)
    {
        return entry != null && !entry.expired() ? entry.value() : null;
    }

    private static long expiresAt(
        Duration ttl)
    {
        return ttl == null ? Long.MAX_VALUE : System.currentTimeMillis() + ttl.toMillis();
    }

    private void defer(
        Runnable task)
    {
        dispatcher.accept(task);
    }
}
