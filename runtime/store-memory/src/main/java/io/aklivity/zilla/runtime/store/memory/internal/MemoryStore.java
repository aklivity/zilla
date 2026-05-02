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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.store.Store;
import io.aklivity.zilla.runtime.engine.store.StoreContext;

final class MemoryStore implements Store
{
    static final String NAME = "memory";

    private final ConcurrentMap<Long, MemoryStorage> storage;

    MemoryStore(
        MemoryStoreConfiguration config)
    {
        this.storage = new ConcurrentHashMap<>();
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
        return new MemoryStoreContext(this::acquireEntries, this::releaseEntries);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/memory.schema.patch.json");
    }

    @Override
    public Optional<NamespaceConfig> contribute(
        EngineConfiguration config)
    {
        final String storeType = config.storeType();
        final String storeName = config.storeName();
        if (!NAME.equals(storeType) || storeName == null)
        {
            return Optional.empty();
        }
        final int colon = storeName.indexOf(':');
        if (colon <= 0 || colon >= storeName.length() - 1)
        {
            return Optional.empty();
        }
        final String namespace = storeName.substring(0, colon);
        final String name = storeName.substring(colon + 1);
        if (!"sys".equals(namespace))
        {
            return Optional.empty();
        }
        return Optional.of(NamespaceConfig.builder()
            .name(namespace)
            .store()
                .name(name)
                .type(NAME)
                .build()
            .build());
    }

    private ConcurrentMap<String, MemoryEntry> acquireEntries(
        long storeId)
    {
        final MemoryStorage memoryStorage = storage.computeIfAbsent(storeId, id -> new MemoryStorage());
        memoryStorage.refs.incrementAndGet();
        return memoryStorage.entries;
    }

    private void releaseEntries(
        long storeId)
    {
        storage.computeIfPresent(storeId, (id, ms) -> ms.refs.decrementAndGet() == 0 ? null : ms);
    }

    private static final class MemoryStorage
    {
        final AtomicInteger refs = new AtomicInteger();
        final ConcurrentMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();
    }
}
