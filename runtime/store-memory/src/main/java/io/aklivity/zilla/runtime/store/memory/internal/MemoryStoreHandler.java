/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.store.memory.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MemoryStoreHandler implements StoreHandler
{
    private final ConcurrentHashMap<String, MemoryEntry> entries;

    MemoryStoreHandler(
        ConcurrentHashMap<String, MemoryEntry> entries)
    {
        this.entries = entries;
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final MemoryEntry entry = entries.get(key);
        final long now = System.currentTimeMillis();
        if (entry == null || entry.isExpired(now))
        {
            if (entry != null && entry.isExpired(now))
            {
                entries.remove(key, entry);
            }
            completion.accept(key, null);
        }
        else
        {
            completion.accept(key, entry.value);
        }
    }

    @Override
    public void put(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        final long expiresAt = ttl == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + ttl;
        entries.put(key, new MemoryEntry(value, expiresAt));
        completion.accept(key);
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        final long now = System.currentTimeMillis();
        final long expiresAt = ttl == Long.MAX_VALUE ? Long.MAX_VALUE : now + ttl;
        final MemoryEntry newEntry = new MemoryEntry(value, expiresAt);
        final MemoryEntry existing = entries.putIfAbsent(key, newEntry);
        if (existing != null && existing.isExpired(now))
        {
            entries.replace(key, existing, newEntry);
            completion.accept(null);
        }
        else if (existing != null)
        {
            completion.accept(existing.value);
        }
        else
        {
            completion.accept(null);
        }
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        entries.remove(key);
        completion.accept(key);
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final MemoryEntry entry = entries.remove(key);
        final long now = System.currentTimeMillis();
        if (entry == null || entry.isExpired(now))
        {
            completion.accept(null);
        }
        else
        {
            completion.accept(entry.value);
        }
    }
}
