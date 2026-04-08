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

import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MemoryStoreHandler implements StoreHandler
{
    private final ConcurrentMap<String, MemoryEntry> entries;

    MemoryStoreHandler(
        ConcurrentMap<String, MemoryEntry> entries)
    {
        this.entries = entries;
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final MemoryEntry entry = entries.get(key);
        final String value = entry != null && !entry.expired() ? entry.value() : null;
        completion.accept(key, value);
    }

    @Override
    public void put(
        String key,
        String value,
        long ttlMillis,
        Consumer<String> completion)
    {
        final long expiresAt = ttlMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + ttlMillis;
        entries.put(key, new MemoryEntry(value, expiresAt));
        completion.accept(null);
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        long ttlMillis,
        Consumer<String> completion)
    {
        final long expiresAt = ttlMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + ttlMillis;
        final MemoryEntry newEntry = new MemoryEntry(value, expiresAt);
        final MemoryEntry existing = entries.putIfAbsent(key, newEntry);
        if (existing != null && existing.expired())
        {
            entries.replace(key, existing, newEntry);
        }
        final String result = existing != null && !existing.expired() ? existing.value() : null;
        completion.accept(result);
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        entries.remove(key);
        completion.accept(null);
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final MemoryEntry entry = entries.remove(key);
        final String value = entry != null && !entry.expired() ? entry.value() : null;
        completion.accept(value);
    }
}
