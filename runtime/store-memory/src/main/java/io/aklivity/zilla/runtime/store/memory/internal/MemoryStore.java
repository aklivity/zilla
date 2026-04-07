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

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.Store;
import io.aklivity.zilla.runtime.engine.store.StoreContext;

final class MemoryStore implements Store
{
    static final String NAME = "memory";

    private final ConcurrentHashMap<String, MemoryEntry> entries;

    MemoryStore(
        MemoryStoreConfiguration config)
    {
        this.entries = new ConcurrentHashMap<>();
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
        return new MemoryStoreContext(this);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/memory.schema.patch.json");
    }

    String get(
        String key)
    {
        final MemoryEntry entry = entries.get(key);
        return entry != null && !entry.expired() ? entry.value() : null;
    }

    void put(
        String key,
        String value,
        long ttlMillis)
    {
        final long expiresAt = ttlMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + ttlMillis;
        entries.put(key, new MemoryEntry(value, expiresAt));
    }

    String putIfAbsent(
        String key,
        String value,
        long ttlMillis)
    {
        final long expiresAt = ttlMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + ttlMillis;
        final MemoryEntry newEntry = new MemoryEntry(value, expiresAt);
        final MemoryEntry existing = entries.putIfAbsent(key, newEntry);
        if (existing != null && existing.expired())
        {
            entries.replace(key, existing, newEntry);
        }
        return existing != null && !existing.expired() ? existing.value() : null;
    }

    void delete(
        String key)
    {
        entries.remove(key);
    }

    String getAndDelete(
        String key)
    {
        final MemoryEntry entry = entries.remove(key);
        return entry != null && !entry.expired() ? entry.value() : null;
    }
}
