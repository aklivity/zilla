/*
 * Copyright 2021-2024 Aklivity Inc
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MemoryStoreHandler implements StoreHandler
{
    private final ConcurrentMap<String, MemoryEntry> entries;
    private final ConcurrentMap<String, List<Watcher>> watchers;
    private final ConcurrentMap<String, LockEntry> locks;
    private final Consumer<Runnable> dispatcher;

    MemoryStoreHandler(
        ConcurrentMap<String, MemoryEntry> entries,
        ConcurrentMap<String, List<Watcher>> watchers,
        ConcurrentMap<String, LockEntry> locks,
        Consumer<Runnable> dispatcher)
    {
        this.entries = entries;
        this.watchers = watchers;
        this.locks = locks;
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final MemoryEntry entry = entries.get(key);
        final String value = entry != null && !entry.expired() ? entry.value() : null;
        defer(() -> completion.accept(key, value));
    }

    @Override
    public void put(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        final long expiresAt = expiresAt(ttl);
        entries.put(key, new MemoryEntry(value, expiresAt));
        notifyWatchers(key, value);
        defer(() -> completion.accept(null));
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        Duration ttl,
        Consumer<String> completion)
    {
        final long expiresAt = expiresAt(ttl);
        final MemoryEntry newEntry = new MemoryEntry(value, expiresAt);
        final MemoryEntry existing = entries.putIfAbsent(key, newEntry);
        boolean stored = existing == null;
        if (existing != null && existing.expired())
        {
            stored = entries.replace(key, existing, newEntry);
        }
        if (stored)
        {
            notifyWatchers(key, value);
        }
        final String result = existing != null && !existing.expired() ? existing.value() : null;
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
        final String value = entry != null && !entry.expired() ? entry.value() : null;
        if (entry != null)
        {
            notifyWatchers(key, null);
        }
        defer(() -> completion.accept(value));
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
        final long now = System.currentTimeMillis();
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
        final long now = System.currentTimeMillis();
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
        return () ->
        {
            final List<Watcher> current = watchers.get(key);
            if (current != null)
            {
                current.remove(watcher);
            }
        };
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

    private static long expiresAt(
        Duration ttl)
    {
        return ttl == null ? Long.MAX_VALUE : System.currentTimeMillis() + ttl.toMillis();
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
