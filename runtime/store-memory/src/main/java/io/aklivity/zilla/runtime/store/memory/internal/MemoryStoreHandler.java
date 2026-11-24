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

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MemoryStoreHandler implements StoreHandler
{
    private final ConcurrentMap<String, MemoryEntry> entries;
    private final ConcurrentMap<String, List<Watcher>> watchers;
    private final ConcurrentMap<String, LockEntry> locks;
    private final MemoryExpirers expirers;
    private final Set<Watch> watches = ConcurrentHashMap.newKeySet();
    private final Set<Expiry> expiries = ConcurrentHashMap.newKeySet();
    private final Consumer<Runnable> dispatcher;
    private final Clock clock;

    MemoryStoreHandler(
        ConcurrentMap<String, MemoryEntry> entries,
        ConcurrentMap<String, List<Watcher>> watchers,
        ConcurrentMap<String, LockEntry> locks,
        MemoryExpirers expirers,
        Consumer<Runnable> dispatcher,
        Clock clock)
    {
        this.entries = entries;
        this.watchers = watchers;
        this.locks = locks;
        this.expirers = expirers;
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.clock = clock;
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final MemoryEntry entry = entries.get(key);
        final String value = entry != null && !entry.expired(clock.millis()) ? entry.value() : null;
        defer(() -> completion.accept(key, value));
    }

    @Override
    public void put(
        String key,
        String value,
        Duration ttl,
        Expiration expiration,
        Consumer<String> completion)
    {
        final MemoryEntry next = new MemoryEntry(value, expiresAt(ttl), tracked(ttl, expiration));
        final MemoryEntry previous = entries.put(key, next);
        expireReplaced(key, previous);
        notifyWatchers(key, value);
        defer(() -> completion.accept(null));
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        Duration ttl,
        Expiration expiration,
        Consumer<String> completion)
    {
        final MemoryEntry newEntry = new MemoryEntry(value, expiresAt(ttl), tracked(ttl, expiration));
        final MemoryEntry existing = entries.putIfAbsent(key, newEntry);
        boolean stored = existing == null;
        if (existing != null && existing.expired(clock.millis()))
        {
            stored = entries.replace(key, existing, newEntry);
            if (stored)
            {
                expireReplaced(key, existing);
            }
        }
        if (stored)
        {
            notifyWatchers(key, value);
        }
        final String result = existing != null && !existing.expired(clock.millis()) ? existing.value() : null;
        defer(() -> completion.accept(result));
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        final MemoryEntry removed = entries.remove(key);
        if (removed != null)
        {
            expireReplaced(key, removed);
            notifyWatchers(key, null);
        }
        defer(() -> completion.accept(null));
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final MemoryEntry entry = entries.remove(key);
        final String value = entry != null && !entry.expired(clock.millis()) ? entry.value() : null;
        if (entry != null)
        {
            expireReplaced(key, entry);
            notifyWatchers(key, null);
        }
        defer(() -> completion.accept(value));
    }

    @Override
    public void increment(
        String key,
        long delta,
        Duration ttl,
        BiConsumer<String, Long> completion)
    {
        final long counter = addAndGet(key, delta, ttl);
        defer(() -> completion.accept(key, counter));
    }

    @Override
    public void get(
        List<String> keys,
        Consumer<List<String>> completion)
    {
        final String[] values = new String[keys.size()];
        for (int index = 0; index < values.length; index++)
        {
            final MemoryEntry entry = entries.get(keys.get(index));
            values[index] = entry != null && !entry.expired(clock.millis()) ? entry.value() : null;
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
            final MemoryEntry entry = entries.remove(key);
            if (entry != null)
            {
                expireReplaced(key, entry);
                notifyWatchers(key, null);
                if (!entry.expired(clock.millis()))
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
        Expiration expiration,
        Consumer<Boolean> completion)
    {
        final boolean replaced = replaceIfEqual(key, expected, value, ttl, expiration);
        if (replaced)
        {
            notifyWatchers(key, value);
        }
        defer(() -> completion.accept(replaced));
    }

    @Override
    public void touch(
        String key,
        Duration ttl,
        Consumer<Boolean> completion)
    {
        final boolean touched = touchIfLive(key, ttl);
        defer(() -> completion.accept(touched));
    }

    @Override
    public void lock(
        String key,
        Duration ttl,
        BiConsumer<String, String> completion)
    {
        final long now = clock.millis();
        final long expiresAt = ttl == null ? Long.MAX_VALUE : now + ttl.toMillis();
        final String token = UUID.randomUUID().toString();
        final LockEntry candidate = new LockEntry(token, expiresAt);
        LockEntry existing = locks.putIfAbsent(key, candidate);
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
        final long now = clock.millis();
        final LockEntry current = locks.get(key);
        final String result;
        if (current != null && current.expiresAt() > now && current.token().equals(token))
        {
            locks.remove(key, current);
            result = token;
        }
        else
        {
            // expired holder still cluttering the map — clean up opportunistically;
            // either way the caller did not prove ownership of an active lock
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
        final long now = clock.millis();
        final LockEntry current = locks.get(key);
        String result = null;
        if (current != null && current.expiresAt() > now && current.token().equals(token))
        {
            final long expiresAt = expiresAt(ttl);
            final LockEntry renewed = new LockEntry(token, expiresAt);
            if (locks.replace(key, current, renewed))
            {
                result = token;
            }
        }
        else if (current != null && current.expiresAt() <= now)
        {
            // expired holder still cluttering the map — clean up opportunistically
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
        final Watcher watcher = new Watcher(listener, dispatcher);
        final List<Watcher> list = watchers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(watcher);
        final Watch watch = new Watch(key, watcher);
        watches.add(watch);
        return watch;
    }

    @Override
    public Closeable expire(
        String prefix,
        BiConsumer<String, String> listener)
    {
        final MemoryExpirer expirer = new MemoryExpirer(Objects.requireNonNull(prefix), listener, dispatcher);
        final Expiry expiry = new Expiry(expirer);
        expirers.add(expirer);
        expiries.add(expiry);
        return expiry;
    }

    void unexpireAll()
    {
        expiries.forEach(Expiry::close);
    }

    void unwatchAll()
    {
        watches.forEach(Watch::close);
    }

    private long addAndGet(
        String key,
        long delta,
        Duration ttl)
    {
        long counter;
        boolean updated;
        boolean live;
        MemoryEntry existing;
        do
        {
            existing = entries.get(key);
            live = existing != null && !existing.expired(clock.millis());
            counter = (live ? Long.parseLong(existing.value()) : 0L) + delta;
            final MemoryEntry next = new MemoryEntry(Long.toString(counter), live ? existing.expiresAt() : expiresAt(ttl),
                !live && ttl == null);
            updated = existing == null ? entries.putIfAbsent(key, next) == null : entries.replace(key, existing, next);
        }
        while (!updated);
        if (!live)
        {
            expireReplaced(key, existing);
        }
        notifyWatchers(key, Long.toString(counter));
        return counter;
    }

    private boolean replaceIfEqual(
        String key,
        String expected,
        String value,
        Duration ttl,
        Expiration expiration)
    {
        final MemoryEntry next = new MemoryEntry(value, expiresAt(ttl), tracked(ttl, expiration));
        boolean replaced;
        boolean contended;
        do
        {
            final MemoryEntry existing = entries.get(key);
            final boolean matches = existing != null && !existing.expired(clock.millis()) && existing.value().equals(expected);
            replaced = matches && entries.replace(key, existing, next);
            contended = matches && !replaced;
        }
        while (contended);
        return replaced;
    }

    private boolean touchIfLive(
        String key,
        Duration ttl)
    {
        MemoryEntry existing;
        boolean live;
        boolean touched;
        boolean contended;
        do
        {
            existing = entries.get(key);
            live = existing != null && !existing.expired(clock.millis());
            touched = live && entries.replace(key, existing, new MemoryEntry(existing.value(), expiresAt(ttl),
                ttl == null || existing.tracked() || existing.expiresAt() == Long.MAX_VALUE));
            contended = live && !touched;
        }
        while (contended);

        if (existing != null && !live && entries.remove(key, existing))
        {
            expireReplaced(key, existing);
        }

        return touched;
    }

    private void expireReplaced(
        String key,
        MemoryEntry replaced)
    {
        if (replaced != null && replaced.tracked() && replaced.expired(clock.millis()))
        {
            expirers.report(key, replaced.value());
        }
    }

    private void notifyWatchers(
        String key,
        String value)
    {
        final List<Watcher> list = watchers.get(key);
        if (list != null && !list.isEmpty())
        {
            for (Watcher w : list)
            {
                w.dispatcher.accept(() -> w.listener.accept(key, value));
            }
        }
    }

    private void defer(
        Runnable task)
    {
        dispatcher.accept(task);
    }

    private static boolean tracked(
        Duration ttl,
        Expiration expiration)
    {
        return Objects.requireNonNull(expiration) == Expiration.TRACKED || ttl == null;
    }

    private long expiresAt(
        Duration ttl)
    {
        return ttl == null ? Long.MAX_VALUE : clock.millis() + ttl.toMillis();
    }

    private final class Watch implements Closeable
    {
        private final String key;
        private final Watcher watcher;

        private Watch(
            String key,
            Watcher watcher)
        {
            this.key = key;
            this.watcher = watcher;
        }

        @Override
        public void close()
        {
            final List<Watcher> current = watchers.get(key);
            if (current != null)
            {
                current.remove(watcher);
            }
            watches.remove(this);
        }
    }

    private final class Expiry implements Closeable
    {
        private final MemoryExpirer expirer;

        private Expiry(
            MemoryExpirer expirer)
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

    record Watcher(
        BiConsumer<String, String> listener,
        Consumer<Runnable> dispatcher)
    {
    }

    record LockEntry(
        String token,
        long expiresAt)
    {
    }
}
